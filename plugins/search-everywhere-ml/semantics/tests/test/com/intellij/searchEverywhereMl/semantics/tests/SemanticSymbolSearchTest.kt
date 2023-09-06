package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.searchEverywhereMl.semantics.services.LocalArtifactsManager
import com.intellij.searchEverywhereMl.semantics.services.SymbolEmbeddingStorage
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import com.intellij.testFramework.utils.editor.saveToDisk
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.util.TimeoutUtil


class SemanticSymbolSearchTest : SemanticSearchBaseTestCase() {
  private val storage
    get() = SymbolEmbeddingStorage.getInstance(project)

  fun `test basic semantics`() {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    assertEquals(5, storage.index.size)

    var neighbours = storage.searchNeighbours("begin indexing", 10, 0.5)
    assertEquals(setOf("startIndexing", "ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).toList()
    assertEquals(setOf("startIndexing", "ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours.map { it.text }.toSet())
  }

  fun `test index ids are not duplicated`() {
    setupTest("java/IndexProjectAction.java", "kotlin/IndexProjectAction.kt")
    assertEquals(1, storage.index.size)
  }

  fun `test method renaming changes the index`() {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    assertEquals(5, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).toList()
    assertEquals(setOf("ProjectIndexingTask", "startIndexing"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("helicopter purchase", 0.5).toList()
    assertEquals(emptySet<String>(), neighbours.map { it.text }.toSet())

    val nameToReplace = "startIndexing"
    val startOffset = myFixture.editor.document.text.indexOf(nameToReplace)
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.document.replaceString(startOffset, startOffset + nameToReplace.length, "buyHelicopter")
      myFixture.editor.document.saveToDisk() // This is how we trigger reindexing
    }

    TimeoutUtil.sleep(1000) // wait for one second for index update

    assertEquals(5, storage.index.size)

    neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).toList()
    assertEquals(setOf("ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("helicopter purchase", 0.5).toList()
    assertEquals(setOf("buyHelicopter"), neighbours.map { it.text }.toSet())
  }

  fun `test removal of file with method changes the index`() {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    assertEquals(5, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).toList()
    assertEquals(setOf("startIndexing", "ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours.map { it.text }.toSet())

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.deleteRecursively() // deletes the currently open file: java/IndexProjectAction.java
    }

    TimeoutUtil.sleep(1000) // wait for one second for index update

    assertEquals(2, storage.index.size)

    neighbours = storage.streamSearchNeighbours("begin indexing", 0.5).toList()
    assertEquals(emptySet<String>(), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours.map { it.text }.toSet())
  }

  private fun setupTest(vararg filePaths: String) {
    myFixture.configureByFiles(*filePaths)
    LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
    SemanticSearchSettings.getInstance().enabledInSymbolsTab = true
    storage.generateEmbeddingsIfNecessary()
  }
}