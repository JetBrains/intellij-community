// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.reference

import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class LinkDestinationReferenceDumbModeTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test file, anchor and web links resolve while the project is dumb`() {
    myFixture.addFileToProject("docs/other.md", "# Other Heading\n")
    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.project) {
      myFixture.configureByText("test.md", TEXT)

      assertResolves("other.md")
      assertResolves("other-heading")
      assertResolves("local)")
      assertResolves("example.com")
    }
  }

  private fun assertResolves(fragment: String) {
    val offset = TEXT.indexOf(fragment)
    assertTrue(offset >= 0, "Fragment '$fragment' was not found")
    timeoutRunBlocking {
      readAction {
        val reference = myFixture.file.findReferenceAt(offset)
        assertNotNull(reference, "No reference at '$fragment'")
        val resolved = when (reference) {
          is PsiPolyVariantReference -> reference.multiResolve(false).isNotEmpty()
          else -> reference!!.resolve() != null
        }
        assertTrue(resolved, "The reference at '$fragment' must resolve in dumb mode")
      }
    }
  }

  private companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    const val TEXT = "# Local\n\n[file](docs/other.md#other-heading) [anchor](#local) <https://example.com>\n"
  }
}
