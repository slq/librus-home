plugins {
    id("com.android.application")
    id("com.chaquo.python")
}
android {
    namespace = "pl.librushome.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "pl.librushome.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.2"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
}
chaquopy {
    defaultConfig {
        version = "3.13"
        val configuredPython = providers.gradleProperty("buildPython").orNull
        if (configuredPython != null) buildPython(configuredPython)
        pip { install("-r", "requirements-android.txt") }
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("junit:junit:4.13.2")
}
