plugins {
    kotlin("js")
}

dependencies {
    implementation(kotlin("stdlib-js"))
    implementation(npm("mermaid", version = "9.3.0"))
    implementation(npm("@mermaid-js/mermaid-mindmap", version = "9.3.0"))
}

kotlin {
    js(IR) {
        useCommonJs()
        browser()
        binaries.executable()
        compilations.all {
            kotlinOptions {
                freeCompilerArgs = freeCompilerArgs + listOf("-opt-in=kotlin.RequiresOptIn")
            }
        }
    }
}
