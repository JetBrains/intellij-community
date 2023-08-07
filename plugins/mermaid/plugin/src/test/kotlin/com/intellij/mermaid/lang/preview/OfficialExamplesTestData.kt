package com.intellij.mermaid.lang.preview

import com.intellij.mermaid.test.OfficialDocumentationExamples
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
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
  fun assumeAvailable() {
    Assumptions.assumeTrue({ obtainBasePath() != null }, "Could not obtain base data path")
  }

  fun obtainExamples(): Map<String, List<VirtualFile>> {
    val base = obtainBasePath()
    checkNotNull(base) { "Failed to obtain base data path" }
    val diagrams = base.children
    return buildMap {
      for (diagram in diagrams) {
        put(diagram.name, diagram.children.toList())
      }
    }
  }

  private fun obtainBasePath(): VirtualFile? {
    val basePath = OfficialDocumentationExamples.obtainBasePath()
    return VfsUtil.findFileByURL(basePath)
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
