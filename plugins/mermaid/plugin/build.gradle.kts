import com.intellij.mermaid.build.*
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.dependency.IdeaDependencyManager
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.intellij.utils.ArchiveUtils
import org.jetbrains.intellij.utils.DependenciesDownloader
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Files
import java.util.Date

plugins {
  java
  `javascript-binaries`
  `example-test-data`
  kotlin("jvm")
  id("org.jetbrains.intellij") version "1.13.3"
  id("org.jetbrains.changelog") version "1.3.1"
  id("org.jetbrains.qodana") version "0.1.13"
  id("org.jetbrains.grammarkit") version "2021.2.2"
}

repositories {
  mavenCentral()
}

dependencies {
  javascriptImplementation(project(":browser:extension", configurations.javascriptBinaries))
  javascriptSourceMaps(project(":browser:extension", configurations.javascriptBinariesSourceMaps))
  testImplementation("junit:junit:4.13.2")
  testImplementation(platform("org.junit:junit-bom:5.9.3"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation(project(":examples:test-data"))
  exampleTestDataImplementation(project(":examples:test-data", configurations.exampleTestData))
}

val generatedGrammarSourcesPath
  get() = buildDir.resolve("grammar").resolve("generated")

sourceSets {
  getByName("main").apply {
    java.srcDirs(
      "src/main",
      generatedGrammarSourcesPath
    )
  }
  val grammar by registering {
    java {
      srcDirs("src/grammar")
    }
  }
}

idea {
  module {
    generatedSourceDirs = generatedSourceDirs + generatedGrammarSourcesPath
  }
}

intellij {
  pluginName.set(properties("pluginName"))
  val platformVersion = System.getenv("PLATFORM_VERSION") ?: properties("platformVersion")
  version.set(platformVersion)
  type.set(properties("platformType"))
  plugins.set(listOf("org.intellij.plugins.markdown"))
}

changelog {
  version.set(project.version as String)
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
  dependsOn(mermaidExtensionBinariesSourceMaps)
}

val jcefLogPath by lazy { buildDir.toPath().resolve("log").resolve("jcef-log-${Date().time}.txt") }

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
    inputs.files(mermaidExtensionBuildResults.map { it.outputs })
  }

  buildSearchableOptions {
    enabled = false
  }

  withType<RunIdeTask> {
    jvmArgs = commonJvmArgs + listOf(
      "-Dide.browser.jcef.debug.port=8080",
      "-DdevelopmentBuild=true"
    )
  }

  buildSearchableOptions {
    jvmArgs = commonJvmArgs
  }

  fun Test.withExampleTestData() {
    jvmArgumentProviders.add {
      val path = configurations.exampleTestDataImplementation.get().resolve().single()
      listOf("-Dexample.test.data.path=$path")
    }
  }

  val modernTests by registering(Test::class) {
    useJUnitPlatform {
      excludeTags("PreviewTest")
    }
  }

  val createJcefLogDirectory by registering(Task::class) {
    doLast {
      Files.createDirectories(jcefLogPath.parent)
    }
  }

  val previewTests by registering(Test::class) {
    dependsOn(createJcefLogDirectory)
    jvmArgs(
      // "-Dintellij.test.standalone=true",
      // "-Djava.awt.headless=false",
      "-Dide.browser.jcef.headless.enabled=true",
      "-Dide.browser.jcef.log.level=verbose",
      "-Dide.browser.jcef.log.path=$jcefLogPath",
      "-Djcef.trace.cefbrowser_n.lifespan=true",
      "-Dide.browser.jcef.debug.js=true",
      // https://bugs.chromium.org/p/chromium/issues/detail?id=638180
      "-Dide.browser.jcef.sandbox.enable=false",
      "-Dide.browser.jcef.gpu.infinitecrash=false"
    )
    useJUnitPlatform {
      includeTags("PreviewTest")
    }
  }

  withType<Test> {
    testLogging {
      jvmArgs(commonJvmArgs)
      showStandardStreams = true
      events(
        TestLogEvent.PASSED,
        TestLogEvent.FAILED,
        TestLogEvent.SKIPPED,
        TestLogEvent.STANDARD_ERROR,
        TestLogEvent.STANDARD_OUT
      )
    }
    withExampleTestData()
    afterSuite { descriptor, result ->
      if (descriptor.parent == null) {
        logger.lifecycle(result.createResultMessage())
      }
    }
  }

  test {
    finalizedBy(modernTests)
    useJUnit()
  }

  generateLexer {
    source.set("src/grammar/lexer/MermaidLexer.flex")
    targetDir.set(generatedGrammarSourcesPath.resolve("com/intellij/mermaid/lang/lexer/").toString())
    targetClass.set("_MermaidLexer")
    // skeleton.set("/some/specific/skeleton")
    purgeOldFiles.set(true)
  }

  generateParser {
    source.set("src/grammar/parser/Mermaid.bnf")
    targetRoot.set(generatedGrammarSourcesPath.toString())
    pathToParser.set("com/intellij/mermaid/lang/parser/_MermaidParser.java")
    pathToPsiRoot.set("com/intellij/mermaid/lang/psi/")
    purgeOldFiles.set(true)
  }

  patchPluginXml {
    val projectVersion = project.version as String
    check(projectVersion != Project.DEFAULT_VERSION) { "Version was not set for plugin subproject" }
    version.set(projectVersion)
    sinceBuild.set(properties("pluginSinceBuild"))
    untilBuild.set(properties("pluginUntilBuild"))

    val readme = project.rootProject.projectDir.resolve("README.md")
    // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
    pluginDescription.set(
      readme.readText().lines().run {
        val start = "[//]: # (Plugin description)"
        val end = "[//]: # (Plugin description end)"
        if (!containsAll(listOf(start, end))) {
          throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
        }
        subList(indexOf(start) + 1, indexOf(end))
      }.joinToString("\n").run { markdownToHTML(this) }
    )

    changeNotes.set(provider {
      changelog.run { getOrNull(properties("pluginVersion")) ?: getLatest() }.toHTML()
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
    val versions = findProperty("pluginVerifierVersions")?.toString()
    val requestedVersions = versions?.split(",")?.map(String::trim) ?: emptyList()
    logger.lifecycle("Requested versions for plugin verification: $requestedVersions")
    // Don't set ideVersions, so we can resolve all dependencies at once by ourselves
    // ideVersions.set(requestedVersions)
    localPaths.set(provider { resolveIdeaDependencies(requestedVersions) })
    // To make sure that the property will be evaluated only once
    localPaths.finalizeValueOnRead()
    offline.set(true)
  }

  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }

  publishPlugin {
    dependsOn("patchChangelog")
    token.set(marketplaceToken)
    val channel = project.publishChannel
    // If IDE is set to a custom plugin repository, it will override the default one,
    // so we won't see updates published to the stable channel (even if their versions are greater).
    // So, just publish all stable versions to the nightly channel as well.
    val channels = mutableSetOf(PublishChannel.NIGHTLY)
    if (channel == PublishChannel.STABLE) {
      channels.add(PublishChannel.STABLE)
    }
    this.channels.set(channels.map { it.actualName })
  }
}

// Not possible to move to the buildSrc, because ij-gradle-plugin uses
// extremely low kotlin language level which prevents setting a dependency on it
fun resolveIdeaDependencies(versions: Collection<String>): List<File> {
  logger.lifecycle("Resolving nightly idea dependencies for versions $versions")
  val dependencyManager = createDependencyManager()
  val dependencies = versions.map { dependencyManager.resolveRemote(project, it) }
  return dependencies.map { it.classes }
}

fun Project.createDependencyManager(): IdeaDependencyManager {
  val archiveUtils = objects.newInstance<ArchiveUtils>()
  val dependencyDownloader = objects.newInstance<DependenciesDownloader>(
    configurations,
    dependencies,
    repositories,
    false
  )
  return objects.newInstance<IdeaDependencyManager>(
    IntelliJPluginConstants.DEFAULT_INTELLIJ_REPOSITORY,
    "",
    archiveUtils,
    dependencyDownloader,
    logCategory()
  )
}

@Suppress("NAME_SHADOWING")
fun IdeaDependencyManager.resolveRemote(
  project: Project,
  version: String
): IdeaDependency {
  val (type, version) = version.trim().split('-', limit = 2)
  return resolveRemote(
    project,
    version,
    type,
    sources = false,
    extraDependencies = emptyList()
  )
}
