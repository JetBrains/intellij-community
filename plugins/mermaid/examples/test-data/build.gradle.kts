import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

plugins {
  java
  kotlin("jvm")
  id("example-test-data")
  id("de.undercouch.download") version "5.4.0"
  id("example-extractor")
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
}

val mermaidVersion: String by project

val mermaidSourcesDownloadPath: Path
  get() = buildDir.resolve("download/mermaid-js-v$mermaidVersion.zip").toPath()

val mermaidSourceUnpackedPath: Path
  get() = buildDir.resolve("unpacked").resolve(mermaidVersion).toPath()

val mermaidSourcesPath: Path
  get() = mermaidSourceUnpackedPath.resolve("mermaid-$mermaidVersion")

val mermaidExamplesPath: Path
  get() = buildDir.resolve("examples").resolve(mermaidVersion).toPath()

exampleExtractor {
  documentPaths.set(provider {
    val documentationPath = mermaidSourcesPath.resolve("packages/mermaid/src/docs/syntax")
    val files = documentationPath.listDirectoryEntries().filterNot { it.isDirectory() }
    return@provider files.filter { it.isMarkdownFile() }
  })
  outputDirectory.set(provider { mermaidExamplesPath })
}

val downloadMermaidSources by tasks.registering(Download::class) {
  src("https://github.com/mermaid-js/mermaid/archive/refs/tags/v$mermaidVersion.zip")
  dest(mermaidSourcesDownloadPath.toFile())
}

val unpackMermaidSources by tasks.registering(Copy::class) {
  dependsOn(downloadMermaidSources)
  from(zipTree(downloadMermaidSources.get().dest))
  into(mermaidSourceUnpackedPath.toFile())
}

fun Path.isMarkdownFile(): Boolean {
  val extension = extension.lowercase()
  return extension == "md" || extension == "markdown"
}

afterEvaluate {
  tasks.extractMermaidExamples {
    mustRunAfter(unpackMermaidSources)
  }
}

val processMermaidExamples by tasks.registering {
  dependsOn(unpackMermaidSources)
  dependsOn(tasks.extractMermaidExamples)
}

artifacts {
  add(configurations.exampleTestData.name, mermaidExamplesPath.toFile()) {
    builtBy(processMermaidExamples)
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

  processResources {
    dependsOn(processMermaidExamples)
    from(provider { mermaidExamplesPath }) {
      into("com/intellij/mermaid/test/examples")
    }
  }
}
