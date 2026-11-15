plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.gohj99.wearnfc.shared"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.wearable)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
