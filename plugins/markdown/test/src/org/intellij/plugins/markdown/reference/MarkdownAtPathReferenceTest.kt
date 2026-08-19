// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownAtPathReferenceTest : BasePlatformTestCase() {
  @Test
  fun `test path resolves from project root`() {
    val target = myFixture.addFileToProject("src/com/example/KotlinClass.kt", "").parent!!

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

    assertFalse(myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset) is FileReference)
  }

  private fun assertReferenceResolves(target: PsiFileSystemItem) {
    val reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assertInstanceOf(reference, FileReference::class.java)
    assertTrue(reference!!.isReferenceTo(target))
  }
}
