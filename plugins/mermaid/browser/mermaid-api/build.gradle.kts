plugins {
  kotlin("js")
}

val mermaidVersion: String by project

dependencies {
  implementation(kotlin("stdlib-js"))
  implementation(npm("mermaid", version = mermaidVersion))
  implementation(npm("@mermaid-js/mermaid-mindmap", version = mermaidVersion))
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
