// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.reference

import com.intellij.openapi.editor.Caret
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderImpl
import org.junit.Assert.assertNotEquals
import java.nio.file.Path

abstract class BaseLinkDestinationReferenceTestCase : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  private fun findFile(path: Path) = with(myFixture) {
    findFileInTempDir(path.toString()).let { if (it.isDirectory) psiManager.findDirectory(it)!! else PsiUtilCore.getPsiFile(project, it) }
  }

  protected open fun getLinksFilePath(): String? = null

  private inline fun testReferenceToFile(filePathFirst: String,
                                         vararg filePathMore: String,
                                         assertions: (PsiReference?, PsiFileSystemItem) -> Unit) =
    testReferenceToFile(Path.of(filePathFirst, *filePathMore), assertions)

  private inline fun testReferenceToFile(filePath: Path, assertions: (PsiReference?, PsiFileSystemItem) -> Unit) {
    val reference = with(myFixture) {
      configureByFile(getLinksFilePath() ?: throw IllegalStateException("Provide the path to the file containing the links"))
      val indexOfLink = file.text.indexOf("[${getTestName(true)}]")
      assertNotEquals(indexOfLink, -1)
      file.findReferenceAt(editor.caretModel.allCarets.map(Caret::getOffset).first { it >= indexOfLink })
    }
    assertions(reference, findFile(filePath))
  }

  protected fun testIsReferenceToFile(targetPathFirst: String, vararg targetPathMore: String) =
    testReferenceToFile(targetPathFirst, *targetPathMore) { reference, file ->
      assertNotNull(reference)
      assertTrue(reference!!.isReferenceTo(file))
    }

  protected fun testIsNotReferenceToFile(undesiredTargetPathFirst: String, vararg undesiredTargetPathMore: String) =
    testReferenceToFile(undesiredTargetPathFirst, *undesiredTargetPathMore) { reference, file ->
      reference?.let { assertFalse(it.isReferenceTo(file)) }
    }

  protected fun testIsReferenceToHeader(filePath: Path, headerName: String) =
    testReferenceToFile(filePath) { reference, file ->
      assertNotNull(reference)
      val header = assertInstanceOf(reference!!.resolve(), MarkdownHeaderImpl::class.java)
      assertTrue(myFixture.psiManager.areElementsEquivalent(file, header.containingFile))
      assertEquals(headerName, header.name)
    }

  protected fun testRenameFile(path: Path, newName: String) {
    val element = findFile(path)
    val testName = getTestName(true)
    val extension = MarkdownFileType.INSTANCE.defaultExtension
    myFixture.configureByFile("$testName.$extension")
    myFixture.renameElement(element, newName)

    myFixture.checkResultByFile("${testName}After.$extension")
  }
}