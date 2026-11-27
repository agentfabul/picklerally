plugins {
    kotlin("jvm")
}

val gdxVersion: String by project
val ktxVersion: String by project
val coroutinesVersion: String by project
val junitVersion: String by project

kotlin {
    jvmToolchain(17)
}

dependencies {
    // libGDX core + Kotlin KTX extensions (no Android or Firebase dependencies allowed here)
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("io.github.libktx:ktx-app:$ktxVersion")
    api("io.github.libktx:ktx-graphics:$ktxVersion")
    api("io.github.libktx:ktx-math:$ktxVersion")
    api("io.github.libktx:ktx-log:$ktxVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
