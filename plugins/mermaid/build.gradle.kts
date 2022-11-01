import de.undercouch.gradle.tasks.download.Download
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String): String {
  return project.findProperty(key).toString()
}

plugins {
  java
  id("org.jetbrains.kotlin.jvm") version "1.7.10"
  id("org.jetbrains.intellij") version "1.9.0"
  id("org.jetbrains.changelog") version "1.3.1"
  id("org.jetbrains.qodana") version "0.1.13"
  id("org.jetbrains.grammarkit") version "2021.2.2"
  id("de.undercouch.download") version "5.2.1"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
  mavenCentral()
}

sourceSets {
  getByName("main").apply {
    java.srcDirs("src/main", "src/grammar", "src/generated")
  }
}

intellij {
  pluginName.set(properties("pluginName"))
  version.set(properties("platformVersion"))
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

val generateLexerAndParser by tasks.registering {
  dependsOn("generateLexer", "generateParser")
}

@OptIn(ExperimentalStdlibApi::class)
val commonJvmArgs = buildList {
  val commonArgs = listOf(
    "-Xmx750m",
    "-Didea.jna.unpacked=true",
    "-Djna.nounpack=true"
  )
  addAll(commonArgs)
  if (OperatingSystem.current().isMacOsX) {
    val path = project.properties["jnaLibsPath"] ?: System.getenv("JNA_LIBS_PATH")
    add("-Djna.boot.library.path=$path")
  }
}

val mermaidVersion = properties("mermaidVersion")
val mermaidArtifactName = "mermaid.min.js"

val mermaidArtifactPath
  get() = buildDir.resolve("mermaid-$mermaidVersion/$mermaidArtifactName")

val downloadMermaidArtifact by tasks.creating(type = Download::class) {
  src("https://cdn.jsdelivr.net/npm/mermaid@$mermaidVersion/dist/$mermaidArtifactName")
  dest(mermaidArtifactPath)
  overwrite(false)
}

val copyMermaidArtifactToResources by tasks.creating(type = Copy::class) {
  dependsOn(downloadMermaidArtifact)
  from(mermaidArtifactPath)
  destinationDir = buildDir.resolve("resources/main/com/intellij/mermaid/jcef")
  to(mermaidArtifactName)
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
    dependsOn("generateLexerAndParser")
  }

  wrapper {
    gradleVersion = properties("gradleVersion")
  }

  withType<RunIdeTask> {
    jvmArgs = commonJvmArgs
  }

  buildSearchableOptions {
    jvmArgs = commonJvmArgs
  }

  withType<ProcessResources> {
    dependsOn(copyMermaidArtifactToResources)
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

    // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
    pluginDescription.set(
      projectDir.resolve("README.md").readText().lines().run {
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

  runIdeForUiTests {
    systemProperty("robot-server.port", "8082")
    systemProperty("ide.mac.message.dialogs.as.sheets", "false")
    systemProperty("jb.privacy.policy.text", "<!--999.999-->")
    systemProperty("jb.consents.confirmation.enabled", "false")
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
