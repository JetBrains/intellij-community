plugins {
    kotlin("js")
}

fun properties(key: String): String {
    return project.findProperty(key).toString()
}

val mermaidVersion = properties("mermaidVersion")

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
