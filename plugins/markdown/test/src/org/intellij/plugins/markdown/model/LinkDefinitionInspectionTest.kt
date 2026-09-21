// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.model

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.intellij.plugins.markdown.model.psi.labels.DuplicateLinkDefinitionInspection
import org.intellij.plugins.markdown.model.psi.labels.UnusedLinkDefinitionInspection
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LinkDefinitionInspectionTest: BasePlatformTestCase() {
  @Test
  fun `definitions are checked during indexing`() {
    RegistryManager.getInstance().get("ide.dumb.aware.inspections").setValue(true, testRootDisposable)
    myFixture.enableInspections(UnusedLinkDefinitionInspection::class.java, DuplicateLinkDefinitionInspection::class.java)
    myFixture.configureByText("definitions.md", """
      [text][used]
      [![image]](https://example.org)

      [used]: https://example.com
      [<warning descr="Duplicate link definition">USED</warning>]: https://example.org
      [<warning descr="Unused link definition">unused</warning>]: https://example.net
      [image]: https://example.com/image.png
    """.trimIndent())
    CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, testRootDisposable)
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      myFixture.checkHighlighting()
    }
  }

  @Test
  fun `the unused inspection can be enabled separately`() {
    myFixture.enableInspections(UnusedLinkDefinitionInspection::class.java)
    myFixture.configureByText("definitions.md", """
      [<warning descr="Unused link definition">label</warning>]: https://example.com
      [label]: https://example.org
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun `the duplicate inspection can be enabled separately`() {
    myFixture.enableInspections(DuplicateLinkDefinitionInspection::class.java)
    myFixture.configureByText("definitions.md", """
      [label]: https://example.com
      [<warning descr="Duplicate link definition">label</warning>]: https://example.org
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun `inspection severities can be configured separately`() {
    myFixture.enableInspections(UnusedLinkDefinitionInspection::class.java, DuplicateLinkDefinitionInspection::class.java)
    val profile = InspectionProfileManager.getInstance(project).currentProfile
    val unusedKey = requireNotNull(HighlightDisplayKey.find("MarkdownUnusedLinkDefinition"))
    val duplicateKey = requireNotNull(HighlightDisplayKey.find("MarkdownDuplicateLinkDefinition"))
    profile.setErrorLevel(unusedKey, HighlightDisplayLevel.WEAK_WARNING, project)
    profile.setErrorLevel(duplicateKey, HighlightDisplayLevel.ERROR, project)
    myFixture.configureByText("definitions.md", """
      [<weak_warning descr="Unused link definition">label</weak_warning>]: https://example.com
      [<error descr="Duplicate link definition">label</error>]: https://example.org
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  @Test
  fun `a definition without references is unused`() {
    doTest("""
      [<warning descr="Unused link definition">unused</warning>]: https://example.com "A title"
    """)
  }

  @Test
  fun `a reference uses a definition before or after it`() {
    for (reference in listOf("[text][label]", "[label][]", "[label]", "![alt][label]", "![label][]", "![label]")) {
      doTest("""
        [label]: https://example.com

        $reference

        [text][later]

        [later]: https://example.org
      """)
    }
  }

  @Test
  fun `reference labels ignore case`() {
    doTest("""
      [text][LaBeL]

      [label]: https://example.com
    """)
  }

  @Test
  fun `image references inside links use their definition`() {
    for (image in listOf("![label]", "![label][]", "![alt][label]")) {
      doTest("""
        [$image](https://example.org)
        [$image][outer]

        [label]: https://example.com/image.png
        [outer]: https://example.org
      """)
    }
  }

  @Test
  fun `outer spaces keep labels distinct as in the preview`() {
    doTest("""
      [text][ label ]

      [<warning descr="Unused link definition">label</warning>]: https://example.com
      [ label ]: https://example.org
    """)
  }

  @Test
  fun `reference labels normalize whitespace`() {
    for (label in listOf("link  label", "link\tlabel", "link\nlabel")) {
      doTest("[text][$label]\n\n[link label]: https://example.com")
    }
  }

  @Test
  fun `a label without letters or digits can be used`() {
    doTest("""
      [text][☕]

      [☕]: https://example.com
    """)
  }

  @Test
  fun `an unused definition is reported beside a used definition`() {
    doTest("""
      [used]

      [used]: https://example.com
      [<warning descr="Unused link definition">unused</warning>]: https://example.com
    """)
  }

  @Test
  fun `text without a reference does not use a definition`() {
    for (text in listOf(
      "label",
      "`[label]`",
      "[label](https://example.com)",
      "![label](https://example.com/image.png)",
      "<!-- [label] -->",
      "\\[label]",
      "[label][other]\n\n[other]: https://example.org",
    )) {
      doTest("$text\n\n[<warning descr=\"Unused link definition\">label</warning>]: https://example.com")
    }
  }

  @Test
  fun `references inside code blocks do not use a definition`() {
    doTest("""
      ```markdown
      [label]
      ```

          [label]

      [<warning descr="Unused link definition">label</warning>]: https://example.com
    """)
  }

  @Test
  fun `a definition title does not use another definition`() {
    doTest("""
      [used]

      [used]: https://example.com "[label]"
      [<warning descr="Unused link definition">label</warning>]: https://example.org
    """)
  }

  @Test
  fun `a reference in another file does not use a definition`() {
    doTest(
      content = """
        [<warning descr="Unused link definition">label</warning>]: https://example.com
      """,
      otherFile = "[label]",
    )
  }

  @Test
  fun `a later definition with the same label is a duplicate`() {
    for (destination in listOf("https://example.com", "https://example.org \"Another title\"")) {
      doTest("""
        [label]

        [label]: https://example.com
        [<warning descr="Duplicate link definition">label</warning>]: $destination
      """)
    }
  }

  @Test
  fun `duplicate labels normalize case and whitespace`() {
    for (label in listOf("LINK LABEL", "link  label", "link\tlabel", "link\nlabel")) {
      doTest(
        "[link label]\n\n[link label]: https://example.com\n" +
        "[<warning descr=\"Duplicate link definition\">$label</warning>]: https://example.org"
      )
    }
  }

  @Test
  fun `matching outer spaces are normalized`() {
    doTest("""
      [text][ label ]

      [  LABEL  ]: https://example.com
      [<warning descr="Duplicate link definition"> label </warning>]: https://example.org
    """)
  }

  @Test
  fun `each later definition is a duplicate even in another section`() {
    doTest("""
      [label]

      # First section

      [label]: https://example.com

      # Second section

      [<warning descr="Duplicate link definition">label</warning>]: https://example.org
      [<warning descr="Duplicate link definition">label</warning>]: https://example.net
    """)
  }

  @Test
  fun `each label gets one diagnostic with the correct style`() {
    val content = """
      [label]: https://example.com "First title"
      [LABEL]: https://example.org "Second title"
      [label]: https://example.net "Third title"
    """.trimIndent()
    myFixture.enableInspections(UnusedLinkDefinitionInspection::class.java, DuplicateLinkDefinitionInspection::class.java)
    myFixture.configureByText("definitions.md", content)
    val diagnostics = myFixture.doHighlighting(HighlightSeverity.WARNING).sortedBy { it.startOffset }.map {
      Triple(it.description, content.substring(it.startOffset, it.endOffset), it.forcedTextAttributesKey ?: it.type.attributesKey)
    }
    assertEquals(
      listOf(
        Triple("Unused link definition", "label", CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES),
        Triple("Duplicate link definition", "LABEL", CodeInsightColors.WARNINGS_ATTRIBUTES),
        Triple("Duplicate link definition", "label", CodeInsightColors.WARNINGS_ATTRIBUTES),
      ),
      diagnostics
    )
  }

  @Test
  fun `different labels with the same destination are not duplicates`() {
    doTest("""
      [first] and [second]

      [first]: https://example.com
      [second]: https://example.com
    """)
  }

  @Test
  fun `a definition in another file is not a duplicate`() {
    doTest(
      content = """
        [label]

        [label]: https://example.com
      """,
      otherFile = "[label]: https://example.org",
    )
  }

  @Test
  fun `definitions in lists and block quotes are checked`() {
    doTest("""
      [used]

      > [used]: https://example.com

      - A list item

        [<warning descr="Unused link definition">unused</warning>]: https://example.org

      [<warning descr="Duplicate link definition">used</warning>]: https://example.net
    """)
  }

  @Test
  fun `code and HTML comments do not declare link labels`() {
    doTest("""
      ```markdown
      [label]: https://example.org
      [label]: https://example.net
      ```

          [label]: https://example.org

      <!--
      [label]: https://example.org
      -->

      [label]

      [label]: https://example.com
    """)
  }

  @Test
  fun `footnotes and comment wrappers are excluded`() {
    doTest("""
      [^note]: A footnote
      [^note]: Another footnote

      [//]: # (A comment)
      [//]: # (Another comment)
    """)
  }

  @Test
  fun `unused warnings update when a reference is added and removed`() {
    myFixture.enableInspections(UnusedLinkDefinitionInspection::class.java)
    myFixture.configureByText("definitions.md", "[label]: https://example.com")
    assertEquals(listOf("Unused link definition"), myFixture.doHighlighting(HighlightSeverity.WARNING).map { it.description })

    val document = myFixture.editor.document
    val reference = "[label]\n\n"
    WriteCommandAction.runWriteCommandAction(project) {
      document.insertString(0, reference)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
    assertEquals(emptyList<String>(), myFixture.doHighlighting(HighlightSeverity.WARNING).map { it.description })

    WriteCommandAction.runWriteCommandAction(project) {
      document.deleteString(0, reference.length)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
    assertEquals(listOf("Unused link definition"), myFixture.doHighlighting(HighlightSeverity.WARNING).map { it.description })
  }

  @Test
  fun `an incomplete definition does not produce a diagnostic`() {
    doTest("""
      [label]:

      [label
    """)
  }

  private fun doTest(content: String, otherFile: String? = null) {
    myFixture.enableInspections(UnusedLinkDefinitionInspection::class.java, DuplicateLinkDefinitionInspection::class.java)
    if (otherFile != null) {
      myFixture.addFileToProject("other.md", otherFile)
    }
    myFixture.configureByText("definitions.md", content.trimIndent())
    myFixture.checkHighlighting()
  }
}
