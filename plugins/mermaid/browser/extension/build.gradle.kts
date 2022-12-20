import com.intellij.mermaid.build.isSourceMap
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
    configureJavascriptBinariesSourceMaps()
}

val browserDistribution: Task?
    get() = tasks.findByName("browserDistribution")

fun collectBrowserDistributionFiles(): FileCollection? {
    return browserDistribution?.outputs?.files
}

fun ArtifactHandler.configureJavascriptBinaries() {
    val files = collectBrowserDistributionFiles() ?: return
    val filesWithoutSourceMaps = files.filterNot { it.isSourceMap() }
    for (file in filesWithoutSourceMaps) {
        add(configurations.javascriptBinaries.name, file) {
            builtBy(browserDistribution)
        }
    }
}

fun ArtifactHandler.configureJavascriptBinariesSourceMaps() {
    val files = collectBrowserDistributionFiles() ?: return
    val sourceMaps = files.filter { it.isSourceMap() }
    for (file in sourceMaps) {
        add(configurations.javascriptBinariesSourceMaps.name, file) {
            builtBy(browserDistribution)
        }
    }
}
