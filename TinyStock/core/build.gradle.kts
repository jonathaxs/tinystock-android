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
    // Os servicos sao suspend: o armazenamento real (Room, na A2) grava fora da thread principal.
    implementation(libs.kotlinx.coroutines.android)
    // Room, DataStore e kotlinx.serialization entram nas levas A2 e A3, quando forem usados.
    testImplementation(libs.junit)
}
