// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "org.beaconcomputing.satellite1"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.beaconcomputing.satellite1"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "0.1.5-orbital-boot"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
