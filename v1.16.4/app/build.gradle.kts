plugins {
    id("com.android.application")
}

android {
    namespace = "ru.zograr.belguschedule"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.zograr.belguschedule"
        minSdk = 23
        targetSdk = 35
        versionCode = 42
        versionName = "1.16.4"
    }
}


dependencies {
    implementation("androidx.core:core:1.13.1")
}
