plugins {
    java
    application
}

group = "dev.akivcraft"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("org.ow2.asm:asm:9.9")
    implementation("com.viaversion:viaversion:5.10.0") { isTransitive = false }
    implementation("com.viaversion:viabackwards:5.10.0") { isTransitive = false }
    implementation("com.viaversion:viarewind:4.1.2") { isTransitive = false }
    compileOnly("org.lwjgl:lwjgl:3.3.3")
    compileOnly("org.lwjgl:lwjgl-opengl:3.3.3")
    compileOnly("org.lwjgl:lwjgl-glfw:3.3.3")
    compileOnly("org.joml:joml:1.10.8")
    compileOnly("io.netty:netty-all:4.1.115.Final")
    compileOnly("com.mojang:authlib:6.0.58")
    compileOnly("com.mojang:brigadier:1.3.10")
    compileOnly("com.mojang:datafixerupper:8.0.16")
    compileOnly("it.unimi.dsi:fastutil:8.5.15")
    compileOnly("org.jspecify:jspecify:1.0.0")
    compileOnly(files("../vanila/26.1.2.jar"))

    // Standalone test runtime dependencies
    runtimeOnly("org.lwjgl:lwjgl:3.3.3")
    runtimeOnly("org.lwjgl:lwjgl-opengl:3.3.3")
    runtimeOnly("org.lwjgl:lwjgl-glfw:3.3.3")
    runtimeOnly("org.lwjgl:lwjgl:3.3.3:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-opengl:3.3.3:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-glfw:3.3.3:natives-linux")
    runtimeOnly("org.joml:joml:1.10.8")
}

application {
    mainClass.set("dev.akivcraft.loader.shader.PipelineStandaloneTest")
}

tasks.jar {
    archiveBaseName.set("akivcraft-loader")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { if (it.isDirectory) it else zipTree(it) }
    })
    manifest {
        attributes(
            "Main-Class" to "dev.akivcraft.loader.AkivCraftMain",
            "Agent-Class" to "dev.akivcraft.loader.AkivCraftAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true"
        )
    }
}
