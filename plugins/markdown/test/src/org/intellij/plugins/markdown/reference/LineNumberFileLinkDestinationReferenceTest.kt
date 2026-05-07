// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class LineNumberFileLinkDestinationReferenceTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  override fun getTestDataPath(): String =
    "${MarkdownTestingUtil.TEST_DATA_PATH}/reference/linkDestination/lineNumber"

  fun `test github single line`() = doTest("github_single_line.md", "Target.cs:L3")

  fun `test bitbucket single line`() = doTest("bitbucket_single_line.md", "Target.cs:L3")

  fun `test bitbucket cloud multi line`() = doTest("bitbucket_multi_reference.md", "Target.cs:L7")

  fun `test bitbucket cloud multi range`() = doTest("bitbucket_multi_range.md", "Target.cs:L7")

  fun `test github line range relative path`() =
    doTest("docs/pages/line_range_relative.md", "MolliePaymentDriver.php:L286")

  private fun doTest(fileName: String, expectedName: String) {
    myFixture.configureByFile(fileName)
    val ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    val resolved = ref?.let { PsiReferenceUtil.unwrapMultiReference(it) }
      ?.firstNotNullOfOrNull { it.resolve() as? PsiNamedElement }
      ?.takeIf { it.name?.matches(Regex(""".*:L\d+""")) == true }
    assertNotNull("Line number reference did not resolve in $fileName", resolved)
    assertEquals(expectedName, resolved!!.name)
    assertEquals(expectedName.substringBefore(":"), resolved.containingFile.name)
  }
}
