package com.intellij.mermaid.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.nio.file.Path
import kotlin.io.path.*

interface ExampleExtractorExtension {
  val documentPaths: Property<Provider<Iterable<Path>>>
  val outputDirectory: Property<Provider<Path>>
}

class ExampleExtractorPlugin: Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("exampleExtractor", ExampleExtractorExtension::class.java)
    project.tasks.register("extractMermaidExamples") {
      doLast {
        processExamples(
          extension.documentPaths.get().get(),
          extension.outputDirectory.get().get()
        )
      }
    }
  }
}

private fun processExamples(paths: Iterable<Path>, outputDirectory: Path) {
  outputDirectory.createDirectories()
  for (path in paths) {
    val name = path.nameWithoutExtension
    val directory = outputDirectory.resolve(name)
    directory.createDirectories()
    val text = path.readText()
    processDocument(text, name, directory)
  }
}

private fun processDocument(text: String, name: String, directory: Path) {
  val examples = collectExamples(text)
  for ((index, example) in examples.withIndex()) {
    val path = directory.resolve("$name-$index.mermaid")
    path.writeText(example)
  }
}
