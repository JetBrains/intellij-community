plugins {
    kotlin("js")
}

dependencies {
    implementation(kotlin("stdlib-js"))
    implementation(project(":browser:mermaid-api"))
    implementation("org.jetbrains:annotations:23.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}

fun properties(key: String): String? {
    return project.findProperty(key)?.toString()
}

val shouldBundleSourceMaps: Boolean
    get() = (project.findProperty("shouldBundleSourceMaps") as? String).toBoolean()

kotlin {
    js(IR) {
        useCommonJs()
        browser {
            commonWebpackConfig {
                showProgress = true
                outputFileName = "mermaid.js"
                sourceMaps = shouldBundleSourceMaps
            }
        }
        binaries.executable()
        compilations.all {
            kotlinOptions {
                freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
            }
        }
    }
}

val mermaidExtensionBundle: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val browserDistribution: Task
    get() = tasks.findByName("browserDistribution")!!

artifacts {
    val files = browserDistribution.outputs.files
    for (file in files) {
        if (file.extension == "map" && !shouldBundleSourceMaps) {
            continue
        }
        add(mermaidExtensionBundle.name, file) {
            builtBy(browserDistribution)
        }
    }
}
