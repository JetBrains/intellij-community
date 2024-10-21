dependencyResolutionManagement {

    // Use Maven Central and Gradle Plugin Portal for resolving dependencies in the shared build logic ("buildSrc") project
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    // Re-use the version catalog from the main build
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "buildSrc"
