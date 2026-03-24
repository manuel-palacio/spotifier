plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "net.palacesoft.spotifier"
    compileSdk = 34

    defaultConfig {
        applicationId = "net.palacesoft.spotifier"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0"

        buildConfigField("String", "BACKEND_BASE_URL", "\"https://spotifier-delicate.fly.dev\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.scalar)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
}
