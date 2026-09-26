plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    signingConfigs {
        create("release") {
            val ks = System.getenv("FITDAILY_KEYSTORE")
            if (!ks.isNullOrBlank()) {
                storeFile = file(ks)
                storePassword = System.getenv("FITDAILY_STORE_PASSWORD")
                keyAlias = System.getenv("FITDAILY_KEY_ALIAS")
                keyPassword = System.getenv("FITDAILY_KEY_PASSWORD")
            }
        }
    }
    namespace = "com.fitdaily.app"
    compileSdk = 36
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        applicationId = "com.fitdaily.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "2.2"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!System.getenv("FITDAILY_KEYSTORE").isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
        }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.health.connect:connect-client:1.1.0")
}

kotlin {
    jvmToolchain(17)
}
