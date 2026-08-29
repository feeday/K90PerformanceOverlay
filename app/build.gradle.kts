plugins {
    id("com.android.application")
}

android {
    namespace = "com.ppt.k90monitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ppt.k90monitor"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "4.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
