plugins {
  kotlin("js")
}

val mermaidVersion: String by project

dependencies {
  implementation(kotlin("stdlib-js"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
  implementation(npm("mermaid", version = mermaidVersion))
  // Use last external mindmap package, so we can maintain its definition in code.
  // This dependency is not actually present in the resulting bundle.
  implementation(npm("@mermaid-js/mermaid-mindmap", version = "9.3.0"))
  implementation(npm("@mermaid-js/mermaid-zenuml", version = "0.2.0"))
}

kotlin {
  js(IR) {
    useCommonJs()
    browser()
    binaries.library()
    compilations.all {
      kotlinOptions {
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
      }
    }
  }
}
