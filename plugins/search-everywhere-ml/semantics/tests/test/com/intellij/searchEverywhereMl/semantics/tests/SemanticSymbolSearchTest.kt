package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ml.embeddings.indexer.FileBasedEmbeddingIndexer
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.indexer.configuration.EmbeddingsConfiguration
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableClass
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticSymbolSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.editor.saveToDisk
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.psi.KtFunction
import kotlin.time.Duration.Companion.seconds


class SemanticSymbolSearchTest : SemanticSearchBaseTestCase() {
  private val storageWrapper
    get() = EmbeddingsConfiguration.getStorageManagerWrapper(IndexId.SYMBOLS)

  private val model
    get() = GotoSymbolModel2(project, testRootDisposable)

  fun `test basic semantics`() = runTest {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    assertEquals(5, storageWrapper.getStorageStats(project).size)

    var neighbours = storageWrapper.search(project, "begin indexing", 10, 0.5f).map { it.id }.toSet()
    assertEquals(setOf("startIndexing", "ProjectIndexingTask"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.toSet()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours)
  }

  fun `test index ids are not duplicated`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/IndexProjectAction.kt")
    assertEquals(1, storageWrapper.getStorageStats(project).size)
  }

  fun `test search everywhere contributor`() = runTest(
    timeout = 45.seconds // increased timeout because of a bug in symbol index
  ) {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")

    val contributor = readAction { SemanticSymbolSearchEverywhereContributor(createEvent()) }
    Disposer.register(project, contributor)
    val searchEverywhereUI = runBlocking(Dispatchers.EDT) { SearchEverywhereUI(project, listOf(contributor), { _ -> null }, null) }
    Disposer.register(project, searchEverywhereUI)

    val elements = runOnEdt { searchEverywhereUI.findElementsForPattern("begin indexing") }.await()

    val items: List<PsiElement> = elements.filterIsInstance<PsiItemWithSimilarity<*>>().mapNotNull { extractPsiElement(it) }
    assertEquals(2, items.size)

    val methods = items.filterIsInstance<PsiMethod>().map { IndexableClass(EntityId(it.name)) } +
                  items.filterIsInstance<KtFunction>().map { IndexableClass(EntityId(it.name ?: "")) } +
                  items.filterIsInstance<PsiClass>().map {
                    IndexableClass(EntityId(it.name ?: ""))
                  } // we might have constructors in the results
    assertEquals(2, methods.size)
    assertEquals(setOf(EntityId("ProjectIndexingTask"), EntityId("startIndexing")), methods.map { it.id }.toSet())
  }

  fun `test method renaming changes the index`() = runTest {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    assertEquals(5, storageWrapper.getStorageStats(project).size)

    var neighbours = storageWrapper.search(project, "begin indexing", 10, 0.5f).map { it.id }.toSet()
    assertEquals(setOf("ProjectIndexingTask", "startIndexing"), neighbours)

    neighbours = storageWrapper.search(project, "helicopter purchase", 10, 0.5f).map { it.id }.toSet()
    assertEquals(emptySet<String>(), neighbours)

    val nameToReplace = "startIndexing"
    val startOffset = myFixture.editor.document.text.indexOf(nameToReplace)
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.document.replaceString(startOffset, startOffset + nameToReplace.length, "buyHelicopter")
      myFixture.editor.document.saveToDisk() // This is how we trigger reindexing
      myFixture.editor.document.commitToPsi(project)
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    neighbours = storageWrapper.search(project, "begin indexing", 10, 0.5f).map{ it.id }.filterByModel()
    assertEquals(setOf("ProjectIndexingTask"), neighbours)

    neighbours = storageWrapper.search(project, "helicopter purchase", 10, 0.5f).map{ it.id }.filterByModel()
    assertEquals(setOf("buyHelicopter"), neighbours)
  }

  fun `test removal of file with method changes the index`() = runTest {
    setupTest("java/ProjectIndexingTask.java", "kotlin/ScoresFileManager.kt")
    assertEquals(5, storageWrapper.getStorageStats(project).size)

    var neighbours = storageWrapper.search(project, "begin indexing", 10, 0.5f).map { it.id }.toSet()
    assertEquals(setOf("startIndexing", "ProjectIndexingTask"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.toSet()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours)

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.deleteRecursively() // deletes the currently open file: java/ProjectIndexingTask.java
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    neighbours = storageWrapper.search(project, "begin indexing", 10, 0.5f).map { it.id }.filterByModel()
    assertEquals(emptySet<String>(), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.filterByModel()
    assertEquals(setOf("handleScoresFile", "clearFileWithScores"), neighbours)
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
    SearchEverywhereSemanticSettings.getInstance().enabledInSymbolsTab = true
    storageWrapper.clearStorage(project)
    FileBasedEmbeddingIndexer.getInstance().prepareForSearch(project).join()
  }
}