package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ml.embeddings.indexer.FileBasedEmbeddingIndexer
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.indexer.configuration.EmbeddingsConfiguration
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableFile
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticFileSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.io.BufferedReader
import java.nio.file.Path
import kotlin.io.path.inputStream

class SemanticFileSearchTest : SemanticSearchBaseTestCase() {
  private val storageWrapper
    get() = EmbeddingsConfiguration.getStorageManagerWrapper(IndexId.FILES)

  private val model
    get() = GotoFileModel(project)

  fun `test basic semantics`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storageWrapper.getStorageStats(project).size)

    var neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.toSet()
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.toSet()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)
  }

  fun `test index ids are not duplicated`() = runTest {
    myFixture.copyFileToProject("java/IndexProjectAction.java", "src/first/IndexProjectAction.java")
    myFixture.copyFileToProject("java/IndexProjectAction.java", "src/second/IndexProjectAction.java")
    setupTest()

    assertEquals(1, storageWrapper.getStorageStats(project).size)
  }

  fun `test search everywhere contributor`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storageWrapper.getStorageStats(project).size)

    val contributor = readAction { SemanticFileSearchEverywhereContributor (createEvent()) }
    Disposer.register(project, contributor)
    val searchEverywhereUI = runBlocking(Dispatchers.EDT) { SearchEverywhereUI (project, listOf(contributor), { _ -> null }, null) }
    Disposer.register(project, searchEverywhereUI)

    val elements = runOnEdt { searchEverywhereUI.findElementsForPattern("index project job") }.await()

    val items: List<PsiElement> = elements.filterIsInstance<PsiItemWithSimilarity<*>>().mapNotNull { extractPsiElement(it) }
    assertEquals(2, items.size)

    val files = items.filterIsInstance<PsiFile>().map { IndexableFile(it.virtualFile) }

    assertEquals(2, files.size)
    assertEquals(setOf(EntityId("IndexProjectAction.java"), EntityId("ProjectIndexingTask.kt")), files.map { it.id }.toSet())
  }

  fun `test file renaming changes the index`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storageWrapper.getStorageStats(project).size)

    var neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.toSet()
    assertEquals(setOf("ProjectIndexingTask.kt", "IndexProjectAction.java"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.toSet()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.rename(this, "ScoresFileHandler.java")
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.filterByModel()
    assertEquals(setOf("ProjectIndexingTask.kt"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.filterByModel()
    assertEquals(setOf("ScoresFileManager.java", "ScoresFileHandler.java"), neighbours)
  }

  fun `test file removal changes the index`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storageWrapper.getStorageStats(project).size)

    var neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.toSet()
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.toSet()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.deleteRecursively() // deletes the currently open file: java/IndexProjectAction.java
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.filterByModel()
    assertEquals(setOf("ProjectIndexingTask.kt"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.filterByModel()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)
  }

  fun `test file creation changes the index`() = runTest {
    setupTest("java/IndexProjectAction.java", "java/ScoresFileManager.java")
    assertEquals(2, storageWrapper.getStorageStats(project).size)

    var neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.toSet()
    assertEquals(setOf("IndexProjectAction.java"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.toSet()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)

    val fileStream = Path.of(testDataPath, "kotlin/ProjectIndexingTask.kt").inputStream()
    myFixture.configureByText("ProjectIndexingTask.kt", fileStream.bufferedReader().use(BufferedReader::readText))

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    assertEquals(3, storageWrapper.getStorageStats(project).size)

    neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.filterByModel()
    assertEquals(setOf("IndexProjectAction.java", "ProjectIndexingTask.kt"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.filterByModel()
    assertEquals(setOf("ScoresFileManager.java"), neighbours)
  }

  private suspend fun Iterable<String>.filterByModel(): Set<String> {
    return filter {
      smartReadAction(project) {
        model.getElementsByName(it, false, it).any { element ->
          (element as PsiElement).isValid
        }
      }
    }.toSet()
  }

  private suspend fun setupTest(vararg filePaths: String) {
    myFixture.configureByFiles(*filePaths)
    SearchEverywhereSemanticSettings.getInstance().enabledInFilesTab = true
    storageWrapper.clearStorage(project)
    FileBasedEmbeddingIndexer.getInstance().prepareForSearch(project).join()
  }
}