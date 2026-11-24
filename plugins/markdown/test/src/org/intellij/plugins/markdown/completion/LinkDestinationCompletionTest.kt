// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.completion

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.application
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class LinkDestinationCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `unclosed link completes a file in the same directory`() {
    myFixture.addFileToProject("foo.txt", "")
    myFixture.configureByText("some.md", "[link title here](fo<caret>")
    myFixture.completeBasic()
    myFixture.checkResult("[link title here](foo.txt<caret>")
  }

  @Test
  fun `closed link completes a file and keeps one closing parenthesis`() {
    myFixture.addFileToProject("foo.txt", "")
    myFixture.configureByText("some.md", "[link title here](fo<caret>)")
    myFixture.completeBasic()
    myFixture.checkResult("[link title here](foo.txt<caret>)")
  }

  @Test
  fun `unclosed point enclosed link offers files with spaces`() {
    myFixture.addFileToProject("my file.md", "")
    myFixture.addFileToProject("my final.md", "")
    myFixture.configureByText("some.md", "[x](<my fi<caret>")
    val variants = myFixture.completeBasic().orEmpty()
    assertThat(variants.map { it.lookupString }).contains("my%20file.md", "my%20final.md")
    application.invokeAndWait {
      myFixture.lookup.currentItem = variants.first { it.lookupString == "my%20file.md" }
      myFixture.type('\n')
    }
    myFixture.checkResult("[x](<my%20file.md<caret>")
  }

  @Test
  fun `plain destination with a space offers files`() {
    myFixture.addFileToProject("my file.md", "")
    myFixture.addFileToProject("my final.md", "")
    myFixture.configureByText("some.md", "[x](my fi<caret>")
    val variants = myFixture.completeBasic().orEmpty().map { it.lookupString }
    assertThat(variants).contains("my%20file.md", "my%20final.md")
  }

  @Test
  fun `unclosed link keeps encoded path completion`() {
    myFixture.addFileToProject("my file.md", "")
    myFixture.addFileToProject("my final.md", "")
    myFixture.configureByText("some.md", "[x](my%20fi<caret>")
    val variants = myFixture.completeBasic().orEmpty().map { it.lookupString }
    assertThat(variants).contains("my%20file.md", "my%20final.md")
  }

  @Test
  fun `closed link completes a file with the caret before an underscore`() {
    myFixture.addFileToProject("scratch_1.md", "")
    myFixture.addFileToProject("scratch_2.md", "")
    myFixture.configureByText("some.md", "[Test](scratch<caret>_)")
    val variants = myFixture.completeBasic().orEmpty().map { it.lookupString }
    assertThat(variants).contains("scratch_1.md", "scratch_2.md")
  }

  @Test
  fun `text after a stray bracket and parenthesis gets no file variants`() {
    myFixture.addFileToProject("foo.txt", "")
    myFixture.configureByText("some.md", "foo](bar fo<caret>")
    val variants = myFixture.completeBasic()
    assertThat(variants.orEmpty()).isEmpty()
    myFixture.checkResult("foo](bar fo<caret>")
  }
}