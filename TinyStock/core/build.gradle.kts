plugins {
    // AGP 9 traz o Kotlin embutido: nao aplicar o plugin Kotlin separado.
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.jonathaxs.tinystock.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Room, DataStore e kotlinx.serialization entram nas levas A1 a A3, quando forem usados.
    testImplementation(libs.junit)
}
