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
        versionCode = providers.gradleProperty("librusVersionCode").orElse("19").get().toInt()
        versionName = providers.gradleProperty("librusVersionName").orElse("0.12.1").get()
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".tools/update-test-assets"))
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug") // Retain the existing local certificate: no data-destructive reinstall.
        }
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
