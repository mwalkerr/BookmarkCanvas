plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "org.mwalker.bookmarkcanvas"
version = "1.0.2"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
//    version.set("2022.2.3")
    version.set("2023.2.6")
    type.set("IC") // Target IDE Platform

    updateSinceUntilBuild.set(false)
    plugins.set(listOf(/* Plugin Dependencies */))
}
tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("222")
        changeNotes.set("""
            First version of the Bookmark Canvas plugin for visual code exploration.
        """.trimIndent())
    }
}