// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.model.psi.labels.UnresolvedLinkLabelInspection
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class UnresolvedLinkLabelInspectionTest: BasePlatformTestCase() {
  @Test
  fun `test unresolved label of full reference link is reported`() {
    doTest("""
      Here is a link to [Google][<warning descr="Cannot resolve link label google">google</warning>].
    """)
  }

  @Test
  fun `test resolved label of full reference link is not reported`() {
    doTest("""
      Here is a link to [Google][google].

      [google]: https://google.com
    """)
  }

  @Test
  fun `test standalone bracketed text is not reported`() {
    doTest("""
      T030 [P] Implement the thing
    """)
  }

  @Test
  fun `test bracketed text separated by a space is not a reference link`() {
    doTest("""
      T030 [P] [US3] Implement the thing
    """)
  }

  @Test
  fun `test several bracketed texts separated by spaces are not reference links`() {
    doTest("""
      T030 [P] [US3] [US4] Implement the thing
    """)
  }

  @Test
  fun `test bracketed text separated by a space after an image marker is not a reference link`() {
    doTest("""
      T030 ![P] [US3] Implement the thing
    """)
  }

  @Test
  fun `test bracketed text separated by a space inside a list item is not a reference link`() {
    doTest("""
      - [ ] T030 [P] [US3] Implement the thing
    """)
  }

  private fun doTest(content: String) {
    myFixture.enableInspections(UnresolvedLinkLabelInspection())
    myFixture.configureByText("some.md", content.trimIndent())
    myFixture.checkHighlighting()
  }
}
