// Gralde file just to be able to run project-model-updater on TeamCity.
// (probably, it's possible to run arbitrary `main`/runConfiguration on TeamCity but I don't know how to do it yet)

pluginManagement {
    repositories {
        maven(url = "https://cache-redirector.jetbrains.com/plugins.gradle.org")
        maven(url = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    }
}
