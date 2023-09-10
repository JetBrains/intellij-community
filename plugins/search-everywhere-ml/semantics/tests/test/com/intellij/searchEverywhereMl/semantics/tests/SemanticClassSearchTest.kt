package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.ide.actions.searcheverywhere.PsiItemWithSimilarity
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.searchEverywhereMl.semantics.contributors.SemanticClassSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.services.ClassEmbeddingsStorage
import com.intellij.searchEverywhereMl.semantics.services.IndexableClass
import com.intellij.searchEverywhereMl.semantics.services.LocalArtifactsManager
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.utils.editor.saveToDisk
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.util.TimeoutUtil
import org.jetbrains.kotlin.psi.KtClass

class SemanticClassSearchTest : SemanticSearchBaseTestCase() {
  private val storage
    get() = ClassEmbeddingsStorage.getInstance(project)

  fun `test basic semantics`() {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.searchNeighbours("index project job", 10, 0.5)
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager"), neighbours.map { it.text }.toSet())
  }

  fun `test index ids are not duplicated`() {
    setupTest("java/IndexProjectAction.java", "kotlin/IndexProjectAction.kt")
    assertEquals(1, storage.index.size)
  }

  fun `test search everywhere contributor`() {
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

  fun `test class renaming changes the index`() {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager"), neighbours.map { it.text }.toSet())

    val nameToReplace = "IndexProjectAction"
    val startOffset = myFixture.editor.document.text.indexOf(nameToReplace)
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.document.replaceString(startOffset, startOffset + nameToReplace.length, "ScoresFileHandler")
      myFixture.editor.document.saveToDisk() // This is how we trigger reindexing
    }

    TimeoutUtil.sleep(1000) // wait for one second for index update

    assertEquals(3, storage.index.size)

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager", "ScoresFileHandler"), neighbours.map { it.text }.toSet())
  }

  fun `test removal of file with class changes the index`() {
    setupTest("java/IndexProjectAction.java", "kotlin/ProjectIndexingTask.kt", "java/ScoresFileManager.java")
    assertEquals(3, storage.index.size)

    var neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("IndexProjectAction", "ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager"), neighbours.map { it.text }.toSet())

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.editor.virtualFile.deleteRecursively() // deletes the currently open file: java/IndexProjectAction.java
    }

    TimeoutUtil.sleep(1000)

    assertEquals(2, storage.index.size)

    neighbours = storage.streamSearchNeighbours("index project job", 0.5).toList()
    assertEquals(setOf("ProjectIndexingTask"), neighbours.map { it.text }.toSet())

    neighbours = storage.streamSearchNeighbours("handle file with scores", 0.4).toList()
    assertEquals(setOf("ScoresFileManager"), neighbours.map { it.text }.toSet())
  }

  private fun setupTest(vararg filePaths: String) {
    myFixture.configureByFiles(*filePaths)
    LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary()
    SemanticSearchSettings.getInstance().enabledInClassesTab = true
    storage.generateEmbeddingsIfNecessary()
  }
}