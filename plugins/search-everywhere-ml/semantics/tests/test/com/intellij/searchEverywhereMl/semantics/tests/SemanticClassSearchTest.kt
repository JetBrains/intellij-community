package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.util.gotoByName.GotoClassModel2
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
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticClassSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.editor.saveToDisk
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.psi.KtClass
import kotlin.time.Duration.Companion.seconds

class SemanticClassSearchTest : SemanticSearchBaseTestCase() {
  private val storageWrapper
    get() = EmbeddingsConfiguration.getStorageManagerWrapper(IndexId.CLASSES)

  private val model
    get() = GotoClassModel2(project)

  fun `test basic semantics`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storageWrapper.getStorageStats(project).size)

    var neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.toSet()
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.toSet()
    assertEquals(setOf("ScoresFileManager"), neighbours)
  }

  fun `test index ids are not duplicated`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/IndexProjectAction.kt")
    assertEquals(1, storageWrapper.getStorageStats(project).size)
  }

  fun `test search everywhere contributor`() = runTest(
    timeout = 45.seconds // increased timeout because of a bug in class index
  ) {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storageWrapper.getStorageStats(project).size)

    val contributor = readAction { SemanticClassSearchEverywhereContributor(createEvent()) }
    Disposer.register(project, contributor)
    val searchEverywhereUI = runBlocking(Dispatchers.EDT) { SearchEverywhereUI(project, listOf(contributor), { _ -> null }, null) }
    Disposer.register(project, searchEverywhereUI)

    val elements = runBlocking(Dispatchers.EDT) { searchEverywhereUI.findElementsForPattern("index project job") }.get()

    val items: List<PsiElement> = elements.filterIsInstance<PsiItemWithSimilarity<*>>().mapNotNull { extractPsiElement(it) }
    assertEquals(2, items.size)

    val classes = items.filterIsInstance<PsiClass>().map { readAction { IndexableClass(EntityId(it.name ?: "")) } } +
                  items.filterIsInstance<KtClass>().map { readAction { IndexableClass(EntityId(it.name ?: "")) } }
    assertEquals(2, classes.size)
    assertEquals(setOf(EntityId("IndexProjectAction"), EntityId("ProjectIndexingTask")), classes.map { it.id }.toSet())
  }

  fun `test class renaming changes the index`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storageWrapper.getStorageStats(project).size)

    var neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.toSet()
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.toSet()
    assertEquals(setOf("ScoresFileManager"), neighbours)

    val nameToReplace = "IndexProjectAction"
    val startOffset = myFixture.editor.document.text.indexOf(nameToReplace)
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.document.replaceString(startOffset, startOffset + nameToReplace.length, "ScoresFileHandler")
      myFixture.editor.document.saveToDisk() // This is how we trigger reindexing
      myFixture.editor.document.commitToPsi(project)
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.filterByModel()
    assertEquals(setOf("ProjectIndexingTask"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.filterByModel()
    assertEquals(setOf("ScoresFileManager", "ScoresFileHandler"), neighbours)
  }

  fun `test removal of file with class changes the index`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storageWrapper.getStorageStats(project).size)

    var neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.toSet()
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.toSet()
    assertEquals(setOf("ScoresFileManager"), neighbours)

    VfsTestUtil.deleteFile(myFixture.editor.virtualFile) // deletes the currently open file: java/IndexProjectAction.java
    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    neighbours = storageWrapper.search(project, "index project job", 10, 0.5f).map { it.id }.filterByModel()
    assertEquals(setOf("ProjectIndexingTask"), neighbours)

    neighbours = storageWrapper.search(project, "handle file with scores", 10, 0.4f).map { it.id }.filterByModel()
    assertEquals(setOf("ScoresFileManager"), neighbours)
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
    SearchEverywhereSemanticSettings.getInstance().enabledInClassesTab = true
    storageWrapper.clearStorage(project)
    FileBasedEmbeddingIndexer.getInstance().prepareForSearch(project).join()
  }
}