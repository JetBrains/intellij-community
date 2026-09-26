import com.intellij.mermaid.build.isSourceMap
import com.intellij.mermaid.build.shouldBundleFullSourceMaps
import org.jetbrains.kotlin.gradle.targets.js.webpack.WebpackDevtool

plugins {
  `javascript-binaries`
  kotlin("js")
}

dependencies {
  implementation(kotlin("stdlib-js"))
  implementation(project(":browser:mermaid-api"))
  implementation("org.jetbrains:annotations:23.1.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
  implementation(devNpm("terser-webpack-plugin", "5.3.6"))
  implementation(devNpm("css-loader", "6.8.1"))
  implementation(devNpm("style-loader", "3.3.3"))
  implementation(npm("@fortawesome/fontawesome-free", "6.4.0"))
}

kotlin {
  js(IR) {
    useCommonJs()
    browser {
      commonWebpackConfig {
        showProgress = true
        outputFileName = "mermaid.js"
        sourceMaps = true
        devtool = when {
          shouldBundleFullSourceMaps -> WebpackDevtool.SOURCE_MAP
          // Will only include names of classes and methods
          else -> WebpackDevtool.NOSOURCES_SOURCE_MAP
        }
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
