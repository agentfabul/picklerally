plugins {
    id("com.android.application")
    kotlin("android")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val gdxVersion: String by project
val firebaseBomVersion: String by project
val coroutinesVersion: String by project
val natives: Configuration by configurations.creating

android {
    namespace = "com.picklerally.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.picklerally.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("assets")
            jniLibs.srcDirs("libs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")

    implementation(platform("com.google.firebase:firebase-bom:$firebaseBomVersion"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutinesVersion")

    // Nearby 1v1 (offline): Wi-Fi / hotspot / Bluetooth discovery and connection, no server needed
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
}

val copyAndroidNatives by tasks.registering {
    doFirst {
        val abiDirs = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        abiDirs.forEach { file("libs/$it").mkdirs() }

        natives.files.forEach { jar ->
            val outputDir = when {
                jar.name.contains("armeabi-v7a") -> file("libs/armeabi-v7a")
                jar.name.contains("arm64-v8a") -> file("libs/arm64-v8a")
                jar.name.contains("x86_64") -> file("libs/x86_64")
                jar.name.contains("x86") -> file("libs/x86")
                else -> null
            }
            if (outputDir != null) {
                copy {
                    from(zipTree(jar))
                    into(outputDir)
                    include("*.so")
                }
            }
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
