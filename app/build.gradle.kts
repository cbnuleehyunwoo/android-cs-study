plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.sparsearraylab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.sparsearraylab"
        minSdk = 30
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
