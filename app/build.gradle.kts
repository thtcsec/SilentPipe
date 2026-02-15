plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.chaquopy)
    id("kotlin-kapt")
}

android {
    namespace = "com.tuhoang.silentpipe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tuhoang.silentpipe"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "trustedrelease"
            keyAlias = "silentpipe"
            keyPassword = "trustedrelease"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // kotlinOptions is deprecated, moved configuration to tasks.withType outside this block

    buildFeatures {
        buildConfig = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

chaquopy {
    defaultConfig {
        version = "3.10"
        pip {
            install("yt-dlp")
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.gson)

    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation(libs.room.runtime)
    //noinspection KaptUsageInsteadOfKsp
    kapt(libs.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
