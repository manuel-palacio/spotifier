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

    signingConfigs {
        create("release") {
            storeFile = file("${System.getProperty("user.home")}/spotifier-release.jks")
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = "spotifier"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
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
