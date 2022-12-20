import com.intellij.mermaid.build.shouldBundleSourceMaps

plugins {
    `javascript-binaries`
    kotlin("js")
}

dependencies {
    implementation(kotlin("stdlib-js"))
    implementation(project(":browser:mermaid-api"))
    implementation("org.jetbrains:annotations:23.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}

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

artifacts {
    configureJavascriptBinaries()
}

val browserDistribution: Task?
    get() = tasks.findByName("browserDistribution")

fun ArtifactHandler.configureJavascriptBinaries() {
    val files = browserDistribution?.outputs?.files ?: return
    for (file in files) {
        if (file.extension == "map" && !shouldBundleSourceMaps) {
            continue
        }
        add(configurations.javascriptBinaries.name, file) {
            builtBy(browserDistribution)
        }
    }
}
