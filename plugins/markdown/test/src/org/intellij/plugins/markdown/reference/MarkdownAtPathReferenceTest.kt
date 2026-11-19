// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownAtPathReferenceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test path resolves from project root`() {
    val file = myFixture.addFileToProject("src/com/example/KotlinClass.kt", "")
    val target = timeoutRunBlocking { readAction { file.parent!! } }

    myFixture.configureByText("README.md", "See @src/com/exa<caret>mple/KotlinClass.kt")
    assertReferenceResolves(target)
  }

  @Test
  fun `test relative path resolves from current directory`() {
    val target = myFixture.addFileToProject("docs/example.md", "")
    val document = myFixture.addFileToProject("docs/README.md", "See @./exam<caret>ple.md")

    myFixture.configureFromExistingVirtualFile(document.virtualFile)
    assertReferenceResolves(target)
  }

  @Test
  fun `test email does not have path reference`() {
    myFixture.configureByText("README.md", "Contact user@exam<caret>ple.com")

    assertFalse(timeoutRunBlocking { readAction { myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset) } } is FileReference)
  }

  private fun assertReferenceResolves(target: PsiFileSystemItem) {
    timeoutRunBlocking {
      readAction {
        val reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
        assertInstanceOf(FileReference::class.java, reference)
        assertTrue(reference!!.isReferenceTo(target))
      }
    }
  }
}
