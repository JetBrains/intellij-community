package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.toNioPath
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticFileSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.services.FileEmbeddingsStorage
import com.intellij.searchEverywhereMl.semantics.services.IndexableClass
import com.intellij.searchEverywhereMl.semantics.services.LocalArtifactsManager
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.util.TimeoutUtil
import java.io.BufferedReader
import kotlin.io.path.inputStream

class SemanticFileSearchTest : SemanticSearchBaseTestCase() {
  private val storage
    get() = FileEmbeddingsStorage.getInstance(project)

  fun `test basic semantics`() {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.searchNeighbours("index project job", 10, 0.5)
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours.toIdsSet())

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours.toIdsSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager.java"), neighbours.toIdsSet())
  }

  fun `test index ids are not duplicated`() {
    myFixture.copyFileToProject("java/IndexProjectAction.java", "src/first/IndexProjectAction.java")
    myFixture.copyFileToProject("java/IndexProjectAction.java", "src/second/IndexProjectAction.java")
    setupTest()

    assertEquals(1, storage.index.size)
  }

  fun `test search everywhere contributor`() {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    val searchEverywhereUI = SearchEverywhereUI(project, listOf(SemanticFileSearchEverywhereContributor(createEvent())),
                                                { _ -> null }, null)
    val elements = PlatformTestUtil.waitForFuture(searchEverywhereUI.findElementsForPattern("index project job"))
    assertEquals(2, elements.size)

    val items: List<PsiElement> = elements.filterIsInstance<PsiItemWithSimilarity<*>>().mapNotNull { extractPsiElement(it) }
    assertEquals(2, items.size)

    val methods = items.filterIsInstance<PsiFile>().map { IndexableClass(it.name) }

    assertEquals(2, methods.size)
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), methods.map { it.id }.toSet())
  }

  fun `test file renaming changes the index`() {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours.toIdsSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager.java"), neighbours.toIdsSet())

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.rename(this, "ScoresFileHandler.java")
    }

    TimeoutUtil.sleep(1000) // wait for one second for index update

    assertEquals(3, storage.index.size)

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("ProjectIndexingTask.kt"), neighbours.toIdsSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager.java", "ScoresFileHandler.java"), neighbours.toIdsSet())
  }

  fun `test file removal changes the index`() {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours.toIdsSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager.java"), neighbours.toIdsSet())

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.deleteRecursively() // deletes the currently open file: java/IndexProjectAction.java
    }

    TimeoutUtil.sleep(1000) // wait for one second for index update

    assertEquals(2, storage.index.size)

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("ProjectIndexingTask.kt"), neighbours.toIdsSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager.java"), neighbours.toIdsSet())
  }

  fun `test file creation changes the index`() {
    setupTest("java/IndexProjectAction.java", "java/ScoresFileManager.java")
    assertEquals(2, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("IndexProjectAction.java"), neighbours.toIdsSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager.java"), neighbours.toIdsSet())

    val fileStream = testDataPath.toNioPath().resolve("kotlin/ProjectIndexingTask.kt").inputStream()
    myFixture.configureByText("ProjectIndexingTask.kt", fileStream.bufferedReader().use(BufferedReader::readText))

    TimeoutUtil.sleep(1000) // wait for one second for index update

    assertEquals(3, storage.index.size)

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours.toIdsSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager.java"), neighbours.toIdsSet())
  }

  private fun setupTest(vararg filePaths: String) {
    myFixture.configureByFiles(*filePaths)
    LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
    SemanticSearchSettings.getInstance().enabledInFilesTab = true
    storage.generateEmbeddingsIfNecessary()
  }
}