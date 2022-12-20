plugins {
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
        browser()
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
    // val targetFile = File(browserDistribution.outputs.files, "extension.js")
    val files = browserDistribution.outputs.files
    for (file in files) {
        add(mermaidExtensionBundle.name, file) {
            builtBy(browserDistribution)
        }
    }
}
