import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ksPath = providers.environmentVariable("ZERNEX_KEYSTORE_FILE").orNull
val ksPassword = providers.environmentVariable("ZERNEX_KEYSTORE_PASSWORD").orNull
val keyAliasValue = providers.environmentVariable("ZERNEX_KEY_ALIAS").orNull
val keyPasswordValue = providers.environmentVariable("ZERNEX_KEY_PASSWORD").orNull
val hasSigning = listOf(ksPath, ksPassword, keyAliasValue, keyPasswordValue).all { !it.isNullOrBlank() }

android {
    namespace = "com.zernex.files"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.zernex.files"
        minSdk = 23
        targetSdk = 36
        versionCode = 1100
        versionName = "1.1.0"
        manifestPlaceholders["requestLegacyExternalStorage"] = "false"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    // La publication Google Play reste ciblée sur l'API 36.
    // La variante Android 10 est un APK de distribution directe uniquement :
    // elle conserve le mode de stockage historique d'Android 10 pour éviter
    // d'obliger l'utilisateur à sélectionner un dossier SAF.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            targetSdk = 36
            versionCode = 1100
            buildConfigField("boolean", "LEGACY_STORAGE", "false")
            manifestPlaceholders["requestLegacyExternalStorage"] = "false"
        }
        create("android10Legacy") {
            dimension = "distribution"
            targetSdk = 29
            versionCode = 1100
            versionName = "1.1.0"
            buildConfigField("boolean", "LEGACY_STORAGE", "true")
            manifestPlaceholders["requestLegacyExternalStorage"] = "true"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        create("release") {
            if (hasSigning) {
                storeFile = file(ksPath!!)
                storePassword = ksPassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }
    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = true
            signingConfig = if (hasSigning) signingConfigs.getByName("release") else null
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}"; excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF" } }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.12")
    implementation("net.lingala.zip4j:zip4j:2.11.6")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

