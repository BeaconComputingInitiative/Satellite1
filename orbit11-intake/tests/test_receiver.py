# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from uuid import uuid4
from datetime import datetime, timezone
import sys
from contextlib import redirect_stdout
from io import StringIO
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from orbit11_receiver import (IntakeDatabase, ValidationError, parse_batch, init_state, make_handler)
from http.server import ThreadingHTTPServer
from threading import Thread
import urllib.request
import urllib.error

class ReceiverTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        with redirect_stdout(StringIO()):
            init_state(self.root)
        self.db = IntakeDatabase(self.root)
    def tearDown(self):
        self.temp.cleanup()
    @staticmethod
    def event(eid=None, facts=None):
        obj={'initiative':'BEACON COMPUTING INITIATIVE','developer_seal':'CJB-DEV-2026',
            'schema':'satellite1.telemetry.v1-candidate','event_id':eid or str(uuid4()),
            'timestamp_utc':datetime.now(timezone.utc).isoformat(),'kind':'CELL_RADIO_OBSERVATION',
            'facts':facts if facts is not None else {'test_only':True}}
        base=json.dumps(obj,separators=(',',':'),ensure_ascii=False).encode()
        obj['record_sha256']=hashlib.sha256(base).hexdigest()
        return json.dumps(obj,separators=(',',':'),ensure_ascii=False).encode()+b'\n'
    def accept(self,blob): return self.db.accept(blob,hashlib.sha256(blob).hexdigest())
    def test_receipt_and_exact_idempotency(self):
        batch=self.event()+self.event()
        a=self.accept(batch); b=self.accept(batch)
        self.assertEqual(a['receipt_id'],b['receipt_id'])
        self.assertEqual(a['accepted_count'],2)
        self.assertTrue(b['replayed'])
    def test_reject_modified_event_same_uuid(self):
        eid=str(uuid4());a=self.event(eid);b=self.event(eid,{'test_only':False})
        self.accept(a)
        with self.assertRaisesRegex(ValidationError,'collision'):
            self.accept(b)
    def test_cross_batch_same_id_same_record_is_allowed(self):
        e=self.event();self.accept(e)
        b=self.accept(self.event()+e)
        self.assertEqual(b['duplicate_count'],1)
    def test_bad_record_digest_is_rejected(self):
        e=self.event().replace(b'"test_only":true',b'"test_only":false')
        with self.assertRaisesRegex(ValidationError,'record SHA'):
            self.accept(e)
    def test_bad_batch_digest_is_rejected(self):
        with self.assertRaisesRegex(ValidationError,'batch digest'):
            parse_batch(self.event(),'0'*64)
    def test_reject_export_manifest(self):
        e=self.event().replace(b'CELL_RADIO_OBSERVATION',b'EXPORT_MANIFEST')
        with self.assertRaises(ValidationError): self.accept(e)
    def test_encrypted_sqlite_does_not_contain_location_secret(self):
        e=self.event(facts={'latitude':'PRIVATE_SENTINEL_987654'})
        self.accept(e)
        with self.db._db() as conn:
            encrypted=conn.execute('SELECT encrypted_jsonl FROM quarantine_events').fetchone()[0]
        self.assertNotIn(b'PRIVATE_SENTINEL',encrypted)
        self.assertIn(b'PRIVATE_SENTINEL',self.db.cipher.decrypt(encrypted))
    def test_duplicate_ids_in_single_batch_denied(self):
        e=self.event()
        with self.assertRaisesRegex(ValidationError,'duplicate event_id'):
            self.accept(e+e)
    def test_invalid_json_is_rejected(self):
        with self.assertRaises(ValidationError): self.accept(b'nope\n')
    def test_http_bearer_gate_and_receipt(self):
        token='local-test-secret'; digest=hashlib.sha256(token.encode()).hexdigest()
        srv=ThreadingHTTPServer(('127.0.0.1',0),make_handler(self.db,digest))
        th=Thread(target=srv.serve_forever,daemon=True);th.start()
        try:
            blob=self.event()
            endpoint=f'http://127.0.0.1:{srv.server_port}/v1/telemetry/batches'
            def req(key):
                return urllib.request.Request(endpoint,method='POST',data=blob,headers={
                    'Authorization':f'Bearer {key}', 'Content-Type':'application/x-ndjson; charset=utf-8',
                    'X-BCI-Developer-Seal':'CJB-DEV-2026',
                    'X-BCI-Batch-SHA256':hashlib.sha256(blob).hexdigest()})
            with self.assertRaises(urllib.error.HTTPError) as denied:
                urllib.request.urlopen(req('invalid'),timeout=3)
            self.assertEqual(denied.exception.code,401)
            with urllib.request.urlopen(req(token),timeout=3) as response:
                receipt=json.load(response)
            self.assertEqual(receipt['accepted_count'],1)
            self.assertTrue(receipt['quarantine'])
        finally:
            srv.shutdown();srv.server_close();th.join(timeout=2)

if __name__=='__main__':unittest.main()
