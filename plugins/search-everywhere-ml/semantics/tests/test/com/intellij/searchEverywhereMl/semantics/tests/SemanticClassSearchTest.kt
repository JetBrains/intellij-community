package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.ml.embeddings.search.services.ClassEmbeddingsStorage
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticClassSearchEverywhereContributor
import com.intellij.platform.ml.embeddings.search.services.IndexableClass
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.platform.ml.embeddings.search.services.SemanticSearchFileChangeListener
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.editor.saveToDisk
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.util.TimeoutUtil
import org.jetbrains.kotlin.psi.KtClass
import kotlinx.coroutines.test.runTest

class SemanticClassSearchTest : SemanticSearchBaseTestCase() {
  private val storage
    get() = ClassEmbeddingsStorage.getInstance(project)

  private val model
    get() = GotoClassModel2(project)

  fun `test basic semantics`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.searchNeighbours("index project job", 10, 0.5).asSequence().filterByModel()
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), neighbours)

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager"), neighbours)
  }

  fun `test index ids are not duplicated`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/IndexProjectAction.kt")
    assertEquals(1, storage.index.size)
  }

  fun `test search everywhere contributor`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    val searchEverywhereUI = SearchEverywhereUI(project, listOf(SemanticClassSearchEverywhereContributor(createEvent())),
                                                { _ -> null }, null)
    val elements = PlatformTestUtil.waitForFuture(searchEverywhereUI.findElementsForPattern("index project job"))
    assertEquals(2, elements.size)

    val items: List<PsiElement> = elements.filterIsInstance<PsiItemWithSimilarity<*>>().mapNotNull { extractPsiElement(it) }
    assertEquals(2, items.size)

    val classes = items.filterIsInstance<PsiClass>().map { IndexableClass(it.name ?: "") } +
                  items.filterIsInstance<KtClass>().map { IndexableClass(it.name ?: "") }
    assertEquals(2, classes.size)
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), classes.map { it.id }.toSet())
  }

  fun `test class renaming changes the index`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager"), neighbours)

    val nameToReplace = "IndexProjectAction"
    val startOffset = myFixture.editor.document.text.indexOf(nameToReplace)
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.document.replaceString(startOffset, startOffset + nameToReplace.length, "ScoresFileHandler")
      myFixture.editor.document.saveToDisk() // This is how we trigger reindexing
      myFixture.editor.document.commitToPsi(project)
    }

    TimeoutUtil.sleep(2000) // wait for four seconds for index update

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("ProjectIndexingTask"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager", "ScoresFileHandler"), neighbours)
  }

  fun `test removal of file with class changes the index`() = runTest {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager"), neighbours)

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.deleteRecursively() // deletes the currently open file: java/IndexProjectAction.java
    }

    TimeoutUtil.sleep(2000) // wait for two seconds for index update

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).filterByModel()
    assertEquals(setOf("ProjectIndexingTask"), neighbours)

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).filterByModel()
    assertEquals(setOf("ScoresFileManager"), neighbours)
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
    SearchEverywhereSemanticSettings.getInstance().enabledInClassesTab = true
    storage.generateEmbeddingsIfNecessary().join()
    SemanticSearchFileChangeListener.getInstance(project).changeEntityTracking(storage, true)
  }
}