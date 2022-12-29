import com.intellij.mermaid.build.*
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    `javascript-binaries`
    kotlin("jvm")
    id("org.jetbrains.intellij") version "1.11.0"
    id("org.jetbrains.changelog") version "1.3.1"
    id("org.jetbrains.qodana") version "0.1.13"
    id("org.jetbrains.grammarkit") version "2021.2.2"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
}

dependencies {
    javascriptImplementation(project(":browser:extension", configurations.javascriptBinaries))
    javascriptSourceMaps(project(":browser:extension", configurations.javascriptBinariesSourceMaps))
}

sourceSets {
    getByName("main").apply {
        java.srcDirs("src/main", "src/grammar", "src/generated")
    }
}

intellij {
    pluginName.set(properties("pluginName"))
    val platformVersion = System.getenv("PLATFORM_VERSION") ?: properties("platformVersion")
    version.set(platformVersion)
    type.set(properties("platformType"))
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList())
}

qodana {
    cachePath.set(projectDir.resolve(".qodana").canonicalPath)
    reportPath.set(projectDir.resolve("build/reports/inspections").canonicalPath)
    saveReport.set(true)
    showReport.set(System.getenv("QODANA_SHOW_REPORT")?.toBoolean() ?: false)
}

val commonJvmArgs = listOf("-Xmx750m")

val generateLexerAndParser by tasks.registering {
    dependsOn(tasks.generateLexer)
    dependsOn(tasks.generateParser)
}

val mermaidExtensionResourcePath
    get() = buildDir.resolve("resources/main/com/intellij/mermaid/markdown/jcef")

val mermaidExtensionResourceDirectory by tasks.registering {
    doLast {
        mkdir(mermaidExtensionResourcePath)
    }
}

val mermaidExtensionBinaries by tasks.registering(Copy::class) {
    dependsOn(mermaidExtensionResourceDirectory)
    from(configurations.javascriptImplementation)
    into(mermaidExtensionResourcePath)
}

val mermaidExtensionBinariesSourceMaps by tasks.registering(Copy::class) {
    dependsOn(mermaidExtensionResourceDirectory)
    from(configurations.javascriptSourceMaps)
    into(mermaidExtensionResourcePath)
}

val mermaidExtensionBuildResults by tasks.registering {
    dependsOn(mermaidExtensionBinaries)
    if (shouldBundleSourceMaps && !isAutomatedProductionBuild) {
        dependsOn(mermaidExtensionBinariesSourceMaps)
    }
}

val ensureThereAreNoSourceMapsAmongResources by tasks.registering {
    mustRunAfter(mermaidExtensionBuildResults)
    doLast {
        val files = mermaidExtensionResourcePath.walkTopDown()
        val sourceMaps = files.filter { it.isSourceMap() }
        check(sourceMaps.toList().isEmpty()) { "Plugin resource directory contains source maps" }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            languageVersion = "1.7"
            apiVersion = "1.7"
        }
    }

    compileKotlin {
        dependsOn(generateLexerAndParser)
    }

    processResources {
        dependsOn(mermaidExtensionBuildResults)
        if (isAutomatedProductionBuild) {
            dependsOn(ensureThereAreNoSourceMapsAmongResources)
        }
        inputs.files(mermaidExtensionBuildResults.map { it.outputs })
    }

    withType<RunIdeTask> {
        jvmArgs = commonJvmArgs
    }

    buildSearchableOptions {
        jvmArgs = commonJvmArgs
    }

    withType<Test> {
        testLogging {
            this.showStandardStreams = true
        }
        // workaround for a Gradle issue, which lets "gradle test" actually run something
        isScanForTestClasses = false
        // Only run tests from classes that end with "Test"
        include("**/*Test.class")
        jvmArgs = commonJvmArgs
    }

    generateLexer {
        source.set("src/grammar/lexer/MermaidLexer.flex")
        targetDir.set("src/generated/com/intellij/mermaid/lang/lexer/")
        targetClass.set("_MermaidLexer")
        // skeleton.set("/some/specific/skeleton")
        purgeOldFiles.set(true)
    }

    generateParser {
        source.set("src/grammar/parser/Mermaid.bnf")
        targetRoot.set("src/generated/")
        pathToParser.set("com/intellij/mermaid/lang/parser/_MermaidParser.java")
        pathToPsiRoot.set("com/intellij/mermaid/lang/psi/")
        purgeOldFiles.set(true)
    }

   patchPluginXml {
       version.set(properties("pluginVersion"))
       sinceBuild.set(properties("pluginSinceBuild"))
       untilBuild.set(properties("pluginUntilBuild"))

       val readme = project.rootProject.projectDir.resolve("README.md")
       // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
       pluginDescription.set(
           readme.readText().lines().run {
               val start = "<!-- Plugin description -->"
               val end = "<!-- Plugin description end -->"

               if (!containsAll(listOf(start, end))) {
                   throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
               }
               subList(indexOf(start) + 1, indexOf(end))
           }.joinToString("\n").run { markdownToHTML(this) }
       )

       changeNotes.set(provider {
           changelog.run {
               getOrNull(properties("pluginVersion")) ?: getLatest()
           }.toHTML()
       })
   }

    changelog {
        val changelogPath = project.rootProject.projectDir.resolve("CHANGELOG.md")
        path.set(changelogPath.toString())
    }

    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    runPluginVerifier {
        ideVersions.set(properties("pluginVerifierVersions").split(",").map(String::trim))
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("MARKETPLACE_TOKEN") ?: "NONE")
        System.getenv("MARKETPLACE_CHANNEL")?.let {
            channels.set(listOf(it))
        }
    }
}
