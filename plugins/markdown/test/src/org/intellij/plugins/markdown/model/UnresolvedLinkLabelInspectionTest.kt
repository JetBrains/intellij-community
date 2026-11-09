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
  fun `inline link label is not reported`() {
    doTest("""
      [Google](https://google.com)
    """)
  }

  @Test
  fun `test resolved label of shortcut reference link is not reported`() {
    doTest("""
      Here is a link to [Google].

      [Google]: https://google.com
    """)
  }

  @Test
  fun `test link label is not reported`() {
    myFixture.addFileToProject("docs/test.kt", "")
    listOf(
      "[@test] docs/test.kt",
      "A requirement uses [@test] docs/test.kt",
      "- A requirement uses [@test] docs/test.kt",
      "- A requirement\n  [@test] docs/test.kt",
      "- A requirement\n  - A nested requirement\n    [@test] docs/test.kt",
    ).forEach(::doTest)
  }

  @Test
  fun `test resolved label of collapsed reference link is not reported`() {
    doTest("""
      Here is a link to [Google][].

      [Google]: https://google.com
    """)
  }

  @Test
  fun `case and whitespace mismatched shortcut label is not reported`() {
    doTest("""
      [foo ]

      [FOO]: https://google.com
    """)
  }

  @Test
  fun `normalization preserves word boundaries`() {
    doTest("""
      [text][<warning descr="Cannot resolve link label foo bar">foo bar</warning>]

      [foobar]: https://example.com
    """)
  }

  @Test
  fun `test unresolved shortcut reference link is not reported`() {
    doTest("""
      ## [Unreleased]
      ## [2.7.18] - 2026-11-04
    """)
  }

  @Test
  fun `test standalone bracketed text is not reported`() {
    doTest("""
      T030 [P] Implement the thing
    """)
  }

  @Test
  fun `test bracketed text separated by a space is not reported`() {
    doTest("""
      T030 [P] [US3] Implement the thing
    """)
  }

  @Test
  fun `test several bracketed texts separated by spaces are not reported`() {
    doTest("""
      T030 [P] [US3] [US4] Implement the thing
    """)
  }

  @Test
  fun `an image and bracketed text separated by a space have separate labels`() {
    doTest("""
      ![<warning descr="Cannot resolve link label P">P</warning>] [US3]
    """)
  }

  @Test
  fun `image reference label is reported`() {
    doTest("""
      ![P][<warning descr="Cannot resolve link label US3">US3</warning>]
    """)
  }

  @Test
  fun `collapsed image reference labels are reported`() {
    doTest("""
      ![<warning descr="Cannot resolve link label image">image</warning>][]
    """)
  }

  @Test
  fun `unresolved image labels inside links are reported`() {
    doTest("""
      [![<warning descr="Cannot resolve link label image">image</warning>]](https://example.org)
      [![<warning descr="Cannot resolve link label image">image</warning>][]](https://example.org)
      [![alt][<warning descr="Cannot resolve link label image">image</warning>]][outer]

      [outer]: https://example.org
    """)
  }

  @Test
  fun `inline image alternate text is not a reference`() {
    doTest("""
      ![alt [text]](https://example.com/image.png)
    """)
  }

  @Test
  fun `a link definition comment does not define a link label`() {
    doTest("""
      [//]

      [//]: # (A comment)
    """)
  }

  @Test
  fun `an incomplete definition does not define a link label`() {
    doTest("""
      [text][<warning descr="Cannot resolve link label label">label</warning>]

      [label]:
    """)
  }

  @Test
  fun `test bracketed text separated by a space inside a list item is not reported`() {
    doTest("""
      - [ ] T030 [P] [US3] Implement the thing
    """)
  }

  @Test
  fun `test link markers are not reference labels`() {
    doTest("""
      [@test] ../../frontend/acp/testSrc/ExampleTest.kt
      [@test] ../../frontend/acp/testSrc/OtherTest.kt (`checks the behavior`)
      - The test checks the result.
        [@test] ../../frontend/acp/testSrc/ResultTest.kt
    """, fileName = "example.spec.md")
  }

  @Test
  fun `test link markers in ordinary Markdown are not reference labels`() {
    doTest("""
      [@test] path/to/ExampleTest.kt
    """)
  }

  @Test
  fun `test markers without a test link remain reference labels`() {
    doTest("""
      [@test]

      [text][<warning descr="Cannot resolve link label @test">@test</warning>]

      [other] path/to/ExampleTest.kt
    """, fileName = "example.spec.md")
  }

  private fun doTest(content: String, fileName: String = "some.md") {
    myFixture.enableInspections(UnresolvedLinkLabelInspection())
    myFixture.configureByText(fileName, content.trimIndent())
    myFixture.checkHighlighting()
  }
}
