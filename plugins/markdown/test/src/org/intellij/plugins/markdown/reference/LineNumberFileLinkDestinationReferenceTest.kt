// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/reference/linkDestination/lineNumber")
class LineNumberFileLinkDestinationReferenceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.copyDirectoryToProject("", "")
  }

  @Test
  fun `test github single line`(): Unit = doTest("github_single_line.md", "Target.cs:L3")

  @Test
  fun `test github single line bare dotfile`(): Unit = doTest("github_bare_dotfile.md", ".env:L5")

  @Test
  fun `test bitbucket single line`(): Unit = doTest("bitbucket_single_line.md", "Target.cs:L3")

  @Test
  fun `test bitbucket cloud multi line`(): Unit = doTest("bitbucket_multi_reference.md", "Target.cs:L7")

  @Test
  fun `test bitbucket cloud multi range`(): Unit = doTest("bitbucket_multi_range.md", "Target.cs:L7")

  @Test
  fun `test github line range relative path`(): Unit = doTest("docs/pages/line_range_relative.md", "MolliePaymentDriver.php:L286")

  private fun doTest(fileName: String, expectedName: String) {
    myFixture.configureByFile(fileName)
    timeoutRunBlocking {
      readAction {
        val ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
        val resolved = ref?.let { PsiReferenceUtil.unwrapMultiReference(it) }
          ?.firstNotNullOfOrNull { it.resolve() as? PsiNamedElement }
          ?.takeIf { it.name?.matches(Regex(""".*:L\d+""")) == true }
        assertNotNull(resolved, "Line number reference did not resolve in $fileName")
        assertEquals(expectedName, resolved!!.name)
        assertEquals(expectedName.substringBefore(":"), resolved.containingFile.name)
      }
    }
  }
}
