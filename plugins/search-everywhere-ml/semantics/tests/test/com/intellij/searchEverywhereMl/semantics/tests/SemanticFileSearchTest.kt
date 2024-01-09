package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.ml.embeddings.search.services.FileEmbeddingsStorage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticFileSearchEverywhereContributor
import com.intellij.platform.ml.embeddings.search.services.IndexableClass
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.platform.ml.embeddings.search.services.SemanticSearchFileChangeListener
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.util.TimeoutUtil
import java.io.BufferedReader
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlinx.coroutines.test.runTest

class SemanticFileSearchTest : SemanticSearchBaseTestCase() {
  private val storage
    get() = FileEmbeddingsStorage.getInstance(project)

  private val model
    get() = GotoFileModel(project)

  fun `test basic semantics`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.searchNeighbours("index project job", 10, 0.5).asSequence().filterByModel()
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours)

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)
  }

  fun `test index ids are not duplicated`() = runTest {
    myFixture.copyFileToProject("java/IndexProjectAction.java", "src/first/IndexProjectAction.java")
    myFixture.copyFileToProject("java/IndexProjectAction.java", "src/second/IndexProjectAction.java")
    setupTest()

    assertEquals(1, storage.index.size)
  }

  fun `test search everywhere contributor`() = runTest {
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

  fun `test file renaming changes the index`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("ProjectIndexingTask.kt", "IndexProjectAction.java"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.rename(this, "ScoresFileHandler.java")
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("ProjectIndexingTask.kt"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager.java", "ScoresFileHandler.java"), neighbours)
  }

  fun `test file removal changes the index`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.deleteRecursively() // deletes the currently open file: java/IndexProjectAction.java
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("ProjectIndexingTask.kt"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)
  }

  fun `test file creation changes the index`() = runTest {
    setupTest("java/IndexProjectAction.java", "java/ScoresFileManager.java")
    assertEquals(2, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("IndexProjectAction.java"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)

    val fileStream = Path.of(testDataPath, "kotlin/ProjectIndexingTask.kt").inputStream()
    myFixture.configureByText("ProjectIndexingTask.kt", fileStream.bufferedReader().use(BufferedReader::readText))

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    assertEquals(3, storage.index.size)

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)
  }

  private fun Sequence<ScoredText>.filterByModel(): Set<String> {
    return map { it.text }.filter {
      model.getElementsByName(it, false, it).any { element ->
        (element as PsiElement).isValid
      }
    }.toSet()
  }

  private suspend fun setupTest(vararg filePaths: String) {
    SemanticSearchFileChangeListener.getInstance(project).clearEvents()
    myFixture.configureByFiles(*filePaths)
    LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
    SearchEverywhereSemanticSettings.getInstance().enabledInFilesTab = true
    storage.generateEmbeddingsIfNecessary().join()
    SemanticSearchFileChangeListener.getInstance(project).changeEntityTracking(storage, true)
  }
}