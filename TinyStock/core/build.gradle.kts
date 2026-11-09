plugins {
    // AGP 9 traz o Kotlin embutido: nao aplicar o plugin Kotlin separado.
    alias(libs.plugins.android.library)
    // Gera o codigo do Room (DAOs e banco).
    alias(libs.plugins.ksp)
    // Leitura e gravacao do backup JSON.
    alias(libs.plugins.kotlin.serialization)
}

ksp {
    // O esquema de cada versao do banco fica no repo, para escrever as migracoes depois.
    arg("room.schemaLocation", "$projectDir/schemas")
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
    // Os servicos sao suspend: o armazenamento real (Room) grava fora da thread principal.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    // Preferencias do app (equivale ao UserDefaults do iOS).
    implementation(libs.androidx.datastore.preferences)
    // Backup JSON compativel com o do iOS.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    // Testes do Room e do DataStore rodam no emulador (connectedDebugAndroidTest).
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}
