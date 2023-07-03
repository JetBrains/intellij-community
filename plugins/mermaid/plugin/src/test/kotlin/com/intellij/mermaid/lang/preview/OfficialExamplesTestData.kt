package com.intellij.mermaid.lang.preview

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

internal object OfficialExamplesTestData {
  private val systemProperty: String?
    get() = System.getProperty("example.test.data.path")

  val testDataPath: Path
    get() = Path(systemProperty!!)

  fun assumeAvailable() {
    Assumptions.assumeTrue(
      { systemProperty != null },
      "'example.test.data.path' system property was not defined"
    )
  }

  fun generateDynamicTests(
    testDataPath: Path,
    acceptExampleCondition: (Path) -> Boolean = { true },
    test: (Path) -> Unit
  ): List<DynamicNode> {
    val diagrams = testDataPath.listDirectoryEntries()
    return diagrams.map { createContainer(it.name, it, acceptExampleCondition, test) }
  }

  private fun createContainer(
    containerName: String,
    diagramExamplesPath: Path,
    acceptPathCondition: (Path) -> Boolean,
    test: (Path) -> Unit
  ): DynamicContainer {
    val examples = diagramExamplesPath.listDirectoryEntries()
    val tests = examples.map { path ->
      DynamicTest.dynamicTest("check ${path.nameWithoutExtension}") {
        Assumptions.assumeTrue(acceptPathCondition(path)) {
          return@assumeTrue "Test was ignored because of condition (${path.nameWithoutExtension} in $containerName)"
        }
        test(path)
      }
    }
    return DynamicContainer.dynamicContainer(containerName, tests)
  }
}
