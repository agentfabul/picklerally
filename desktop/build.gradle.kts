plugins {
    kotlin("jvm")
    application
}

val gdxVersion: String by project

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.picklerally.desktop.DesktopLauncherKt")
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
}

tasks.named<JavaExec>("run") {
    workingDir = file("../android/assets").apply { mkdirs() }
}
