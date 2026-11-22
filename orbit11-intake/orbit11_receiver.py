#!/usr/bin/env python3
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# ORBIT11 intake — authenticated, encrypted, idempotent quarantine receiver.
# Accept a batch IF AND ONLY IF bearer, HTTPS, record and integrity checks pass.
from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import sqlite3
import ssl
import stat
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from datetime import datetime, timezone
from contextlib import contextmanager
from uuid import UUID

from cryptography.fernet import Fernet

SEAL = 'CJB-DEV-2026'
INITIATIVE = 'BEACON COMPUTING INITIATIVE'
API_PATH = '/v1/telemetry/batches'
MAX_BODY = 1024 * 1024
MAX_RECORDS = 100
MAX_LINE = 64 * 1024
DIGEST_RE = re.compile(r'^[a-f0-9]{64}$')
RECORD_SUFFIX = re.compile(rb'^(.*),"record_sha256":"([0-9a-f]{64})"}$', re.DOTALL)

class ValidationError(Exception):
    def __init__(self, message: str, status: int = 422):
        super().__init__(message)
        self.status = status


def _object_with_unique_keys(pairs):
    value = {}
    for key, entry in pairs:
        if key in value:
            raise ValidationError('duplicate JSON key')
        value[key] = entry
    return value


def parse_batch(body: bytes, claimed_digest: str) -> list[tuple[str, bytes, str]]:
    if not isinstance(claimed_digest, str) or not DIGEST_RE.fullmatch(claimed_digest):
        raise ValidationError('invalid batch digest', 400)
    if not body or len(body) > MAX_BODY or not body.endswith(b'\n'):
        raise ValidationError('batch must be nonempty, bounded UTF-8 JSONL with final newline')
    if not hmac.compare_digest(hashlib.sha256(body).hexdigest(), claimed_digest):
        raise ValidationError('batch digest mismatch')
    rows = body.splitlines(keepends=True)
    if not 1 <= len(rows) <= MAX_RECORDS:
        raise ValidationError('invalid batch record count')
    parsed = []
    seen = set()
    for row in rows:
        if not row.endswith(b'\n') or not 0 < len(row) <= MAX_LINE or not row.strip():
            raise ValidationError('invalid JSONL record framing')
        raw = row[:-1]  # Keep exact Kotlin JSON object bytes; no reserialization of identity digest.
        m = RECORD_SUFFIX.fullmatch(raw)
        if m is None:
            raise ValidationError('record SHA-256 must be the final JSON field')
        base, stored = m.groups()
        if not hmac.compare_digest(hashlib.sha256(base + b'}').hexdigest().encode(), stored):
            raise ValidationError('record SHA-256 mismatch')
        try:
            obj = json.loads(raw.decode('utf-8'), object_pairs_hook=_object_with_unique_keys)
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
            raise ValidationError('invalid UTF-8/JSON') from exc
        if not isinstance(obj, dict) or obj.get('initiative') != INITIATIVE or obj.get('developer_seal') != SEAL:
            raise ValidationError('incorrect initiative or seal')
        if obj.get('schema') != 'satellite1.telemetry.v1-candidate':
            raise ValidationError('unapproved schema')
        eid = obj.get('event_id')
        if not isinstance(eid, str):
            raise ValidationError('event_id required')
        try:
            if str(UUID(eid)) != eid.lower():
                raise ValueError('non-canonical UUID')
        except ValueError as exc:
            raise ValidationError('invalid event_id') from exc
        if eid in seen:
            raise ValidationError('duplicate event_id in batch')
        seen.add(eid)
        if not isinstance(obj.get('kind'), str) or not re.fullmatch(r'[A-Z0-9_]{3,48}', obj['kind']):
            raise ValidationError('invalid event kind')
        if obj['kind'] == 'EXPORT_MANIFEST' or not isinstance(obj.get('facts'), dict):
            raise ValidationError('not a telemetry event')
        timestamp = obj.get('timestamp_utc')
        if not isinstance(timestamp, str) or len(timestamp) > 40:
            raise ValidationError('invalid timestamp')
        try:
            from datetime import datetime as _datetime
            instant = _datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
            if instant.utcoffset() is None:
                raise ValueError('timezone missing')
        except (ValueError, OverflowError) as exc:
            raise ValidationError('invalid timestamp') from exc
        parsed.append((eid, row, hashlib.sha256(row).hexdigest()))
    return parsed


class IntakeDatabase:
    def __init__(self, state_dir: Path):
        self.root = state_dir
        if self.root.exists() and self.root.is_symlink():
            raise ValueError('state directory symlink denied')
        self.root.mkdir(mode=0o700, parents=True, exist_ok=True)
        os.chmod(self.root, 0o700)
        keyfile = self.root / 'fernet.key'
        if not keyfile.exists():
            raise ValueError('HOLD: no encryption key. Use --init-state once')
        if keyfile.is_symlink():
            raise ValueError('key symlink denied')
        if stat.S_IMODE(keyfile.stat().st_mode) & 0o077:
            raise ValueError('key permissions too open')
        self.cipher = Fernet(keyfile.read_bytes().strip())
        self.dbpath = self.root / 'quarantine.sqlite3'
        if self.dbpath.is_symlink():
            raise ValueError('database symlink denied')
        with self._db() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS batches (
                    digest TEXT PRIMARY KEY, receipt_id TEXT NOT NULL UNIQUE,
                    record_count INTEGER NOT NULL, created_utc TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS quarantine_events (
                    id TEXT PRIMARY KEY, record_hash TEXT NOT NULL,
                    encrypted_jsonl BLOB NOT NULL, batch_digest TEXT NOT NULL);
                CREATE INDEX IF NOT EXISTS idx_batch ON quarantine_events(batch_digest);
            """)
        os.chmod(self.dbpath, 0o600)

    @contextmanager
    def _db(self):
        db = sqlite3.connect(self.dbpath, timeout=30, isolation_level=None)
        try:
            db.execute('PRAGMA journal_mode=WAL')
            db.execute('PRAGMA synchronous=FULL')
            db.execute('PRAGMA busy_timeout=30000')
            yield db
        finally:
            db.close()

    def accept(self, body: bytes, digest: str) -> dict:
        records = parse_batch(body, digest)
        with self._db() as db:
            db.execute('BEGIN IMMEDIATE')
            prior = db.execute('SELECT receipt_id,record_count FROM batches WHERE digest=?', (digest,)).fetchone()
            if prior:
                db.execute('COMMIT')
                return {'batch_sha256':digest, 'accepted_count': prior[1], 'receipt_id':prior[0], 'replayed':True}
            duplicate_count = 0
            # A mismatched reuse of an event UUID is a hard collision, not a successful delivery.
            for eid, raw, recordhash in records:
                old = db.execute('SELECT record_hash FROM quarantine_events WHERE id=?', (eid,)).fetchone()
                if old and not hmac.compare_digest(old[0], recordhash):
                    raise ValidationError('event_id collision with different content', 409)
                if old:
                    duplicate_count += 1
                else:
                    db.execute('INSERT INTO quarantine_events(id,record_hash,encrypted_jsonl,batch_digest) VALUES (?,?,?,?)',
                               (eid, recordhash, self.cipher.encrypt(raw), digest))
            receipt = secrets.token_urlsafe(24)
            db.execute('INSERT INTO batches(digest,receipt_id,record_count,created_utc) VALUES(?,?,?,?)',
                       (digest, receipt, len(records), datetime.now(timezone.utc).isoformat()))
            db.execute('COMMIT')
            return {'batch_sha256': digest, 'accepted_count':len(records), 'receipt_id':receipt,
                    'duplicate_count': duplicate_count, 'quarantine':True}


def init_state(state: Path):
    if state.exists() and state.is_symlink():
        raise ValueError('state symlink denied')
    state.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(state, 0o700)
    def create(path: Path, data: bytes):
        # O_EXCL avoids overwriting an existing encryption identity or token.
        fd = os.open(str(path), os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(fd,'wb') as fp:
            fp.write(data)
            fp.flush()
            os.fsync(fp.fileno())
    keyfile = state/'fernet.key'
    if not keyfile.exists():
        create(keyfile, Fernet.generate_key() + b'\n')
    tokenfile = state/'device-token.sha256'
    if not tokenfile.exists():
        token = secrets.token_urlsafe(32)
        create(tokenfile, (hashlib.sha256(token.encode()).hexdigest() + '\n').encode())
        print('DEVICE_ENROLLMENT_TOKEN (SHOW ONCE; STORE PRIVATELY):', token)
    else:
        print('EXISTING_TOKEN_PRESERVED: YES (no token printed)')
    IntakeDatabase(state)
    print('STATE_INITIALIZED: PASS')


def make_handler(db: IntakeDatabase, token_sha: str):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'
        server_version = 'BCI-ORBIT11-Intake/0.1'
        def log_message(self, fmt, *args):
            # Never log endpoints with querystrings, credentials, IP addresses or raw event bodies.
            pass
        def send_json(self, status: int, obj: dict):
            payload = json.dumps(obj, separators=(',', ':')).encode()
            self.send_response(status)
            self.send_header('Content-Type','application/json')
            self.send_header('Content-Length',str(len(payload)))
            self.send_header('Cache-Control','no-store')
            self.send_header('Connection','close')
            self.end_headers()
            self.wfile.write(payload)
        def do_POST(self):
            if self.path != API_PATH:
                return self.send_json(404, {'error':'not found'})
            if self.headers.get('Transfer-Encoding'):
                return self.send_json(400,{'error':'chunked encoding unsupported'})
            auth = self.headers.get('Authorization','')
            if not auth.startswith('Bearer ') or not hmac.compare_digest(
                hashlib.sha256(auth[7:].encode()).hexdigest(), token_sha):
                return self.send_json(401, {'error':'unauthorized'})
            if self.headers.get('X-BCI-Developer-Seal','') != SEAL:
                return self.send_json(400, {'error':'incorrect seal'})
            if self.headers.get('Content-Type','').split(';')[0].strip() != 'application/x-ndjson':
                return self.send_json(415,{'error':'unsupported content type'})
            try:
                length = int(self.headers.get('Content-Length','-1'))
            except ValueError:
                length = -1
            if length <= 0 or length > MAX_BODY:
                return self.send_json(413,{'error':'invalid or oversized body'})
            body = self.rfile.read(length)
            if len(body) != length:
                return self.send_json(400,{'error':'truncated body'})
            try:
                receipt = db.accept(body, self.headers.get('X-BCI-Batch-SHA256',''))
            except ValidationError as exc:
                return self.send_json(exc.status, {'error':str(exc)})
            except (OSError, sqlite3.DatabaseError):
                return self.send_json(503, {'error':'durable storage unavailable'})
            return self.send_json(200, receipt)
    return Handler


def main():
    parser = argparse.ArgumentParser(description='BCI ORBIT11 quarantine intake; no neural training')
    parser.add_argument('--state-dir', type=Path, required=True)
    parser.add_argument('--init-state',action='store_true')
    parser.add_argument('--serve',action='store_true')
    parser.add_argument('--bind',default='127.0.0.1')
    parser.add_argument('--port',type=int,default=8765)
    parser.add_argument('--tls-cert',type=Path)
    parser.add_argument('--tls-key',type=Path)
    args=parser.parse_args()
    os.umask(0o077)
    if args.init_state:
        init_state(args.state_dir)
        return
    if not args.serve:
        parser.error('select --init-state or --serve')
    if not (args.tls_cert and args.tls_key):
        parser.error('TLS certificate and private key are mandatory for live serving')
    db=IntakeDatabase(args.state_dir)
    tokenfile=args.state_dir/'device-token.sha256'
    if tokenfile.is_symlink() or stat.S_IMODE(tokenfile.stat().st_mode) & 0o077:
        parser.error('credential file missing or permissions too open')
    token_sha=tokenfile.read_text().strip()
    if not DIGEST_RE.fullmatch(token_sha):
        parser.error('invalid token hash file')
    server=ThreadingHTTPServer((args.bind,args.port),make_handler(db,token_sha))
    server.daemon_threads=True
    ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version=ssl.TLSVersion.TLSv1_2
    ctx.load_cert_chain(str(args.tls_cert),str(args.tls_key))
    server.socket=ctx.wrap_socket(server.socket, server_side=True)
    print(f'{INITIATIVE} | Developer Seal: {SEAL}',flush=True)
    print(f'LISTENING: https://{args.bind}:{args.port}{API_PATH}',flush=True)
    print('INTAKE: QUARANTINE ONLY | TRAINING: HOLD',flush=True)
    server.serve_forever(poll_interval=0.5)

if __name__=='__main__':
    main()
