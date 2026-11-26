// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.markdown.backend.inspections.MarkdownUnresolvedFileReferenceInspection
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class ParenthesesLinkDestinationReferenceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test rename escapes parentheses`() {
    val target = createFile("file.txt")
    myFixture.configureByText("document.md", "[text](file.txt)")
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        myFixture.renameElement(target, "file(1).txt", false, false)
        myFixture.checkResult("[text](file\\(1\\).txt)")
      }
    }
    assertLinkResolvesTo("file(1).txt")
  }

  @Test
  fun `test escaped parentheses resolve`() {
    createFile("file(1).txt")
    myFixture.configureByText("document.md", "[text](file\\(1\\).txt)")
    assertLinkResolvesTo("file(1).txt")
  }

  @Test
  fun `test escaped brackets resolve`() {
    createFile("file[1].txt")
    myFixture.configureByText("document.md", "[text](file\\[1\\].txt)")
    assertLinkResolvesTo("file[1].txt")
  }

  @Test
  fun `test rename updates escaped brackets`() {
    val target = createFile("file[1].txt")
    myFixture.configureByText("document.md", "[text](file\\[1\\].txt)")
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        myFixture.renameElement(target, "other.txt", false, false)
        myFixture.checkResult("[text](other.txt)")
      }
    }
  }

  @Test
  fun `test escaped directory with extensionless link resolves`() {
    createFile("docs(1)/target.md")
    myFixture.configureByText("document.md", "[text](docs\\(1\\)/target)")
    assertLinkResolvesTo("docs(1)")
    assertLinkResolvesTo("docs(1)/target.md")
  }

  @Test
  fun `test completion escapes parentheses`() {
    createFile("file(1).txt")
    myFixture.configureByText("document.md", "[text](file<caret>)")
    myFixture.completeBasic()
    myFixture.checkResult("[text](file\\(1\\).txt<caret>)")
    assertLinkResolvesTo("file(1).txt")
  }

  @Test
  fun `test quick fix escapes parenthesis in renamed reference`() {
    createFile("file(1.txt")
    myFixture.enableInspections(MarkdownUnresolvedFileReferenceInspection::class.java)
    myFixture.configureByText("document.md", "[text](FI<caret>LE\\(1.txt)")
    myFixture.launchAction(myFixture.findSingleIntention(CodeInsightBundle.message("rename.file.reference.text", "file(1.txt")))
    myFixture.checkResult("[text](file\\(1.txt)")
  }

  @Test
  fun `test second rename updates encoded space and escaped parentheses`() {
    val target = createFile("my file.txt")
    val document = createFile("docs/document.md", "[text](../my%20file.txt)")
    myFixture.configureFromExistingVirtualFile(document.virtualFile)
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        myFixture.renameElement(target, "my file(1).txt", false, false)
        myFixture.checkResult("[text](../my%20file\\(1\\).txt)")
        myFixture.renameElement(createdFile("my file(1).txt"), "renamed.txt", false, false)
        myFixture.checkResult("[text](../renamed.txt)")
      }
    }
  }

  private fun createFile(path: String, text: String = ""): PsiFileSystemItem = myFixture.addFileToProject(path, text)

  private fun createdFile(path: String): PsiFileSystemItem {
    return PsiUtilCore.findFileSystemItem(myFixture.project, myFixture.findFileInTempDir(path))!!
  }

  private fun assertLinkResolvesTo(path: String) {
    timeoutRunBlocking {
      readAction {
        val destination = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownLinkDestination::class.java)!!
        assertTrue(destination.references.any { it.isReferenceTo(createdFile(path)) })
      }
    }
  }
}