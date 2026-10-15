// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LinkDestinationReferenceDumbModeTest : BasePlatformTestCase() {
  fun `test file, anchor and web links resolve while the project is dumb`() {
    myFixture.addFileToProject("docs/other.md", "# Other Heading\n")
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      myFixture.configureByText("test.md", TEXT)

      assertResolves("other.md")
      assertResolves("other-heading")
      assertResolves("local)")
      assertResolves("example.com")
    }
  }

  private fun assertResolves(fragment: String) {
    val offset = TEXT.indexOf(fragment)
    assertTrue("Fragment '$fragment' was not found", offset >= 0)
    val reference = myFixture.file.findReferenceAt(offset)
    assertNotNull("No reference at '$fragment'", reference)
    val resolved = when (reference) {
      is PsiPolyVariantReference -> reference.multiResolve(false).isNotEmpty()
      else -> reference!!.resolve() != null
    }
    assertTrue("The reference at '$fragment' must resolve in dumb mode", resolved)
  }

  private companion object {
    const val TEXT = "# Local\n\n[file](docs/other.md#other-heading) [anchor](#local) <https://example.com>\n"
  }
}
