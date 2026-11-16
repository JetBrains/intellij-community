// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.formatter

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.performReformatting
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.runWithTemporaryStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.formatter.ListMarkerPostFormatProcessor
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.formatter.settings.OrderedListMarker
import org.intellij.plugins.markdown.lang.formatter.settings.UnorderedListMarker
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList

class MarkdownListMarkerFormatterTest: LightPlatformCodeInsightTestCase() {
  fun `test keep existing list markers`() = doListMarkerTest(
    UnorderedListMarker.KEEP,
    OrderedListMarker.KEEP,
    LIST_MARKERS_TEXT,
  )

  fun `test force dash and parenthesis list markers`() = doListMarkerTest(
    UnorderedListMarker.DASH,
    OrderedListMarker.PARENTHESIS,
    """
    - star one
    - star two
        - nested plus

    1) dot one
    2) dot two

    - plus one
    - plus two

    1) paren one
    2) paren two

    - [ ] star open

    1) [x] dot done
    """.trimIndent(),
  )

  fun `test force asterisk and dot list markers`() = doListMarkerTest(
    UnorderedListMarker.ASTERISK,
    OrderedListMarker.DOT,
    """
    * star one
    * star two
        * nested plus

    1. dot one
    2. dot two

    * plus one
    * plus two

    1. paren one
    2. paren two

    * [ ] star open

    1. [x] dot done
    """.trimIndent(),
  )

  fun `test force plus list marker keeps ordered list markers`() = doListMarkerTest(
    UnorderedListMarker.PLUS,
    OrderedListMarker.KEEP,
    """
    + star one
    + star two
        + nested plus

    1. dot one
    2. dot two

    + plus one
    + plus two

    1) paren one
    2) paren two

    + [ ] star open

    1. [x] dot done
    """.trimIndent(),
  )

  fun `test force list marker changes the whole list in the reformatted range`() {
    runWithTemporaryStyleSettings(project) { settings ->
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
      configureFromFileText("range.md", "* first\n* second\n\nText\n\n+ third\n+ fourth")
      val firstLine = TextRange(0, "* first".length)
      WriteCommandAction.runWriteCommandAction(project) {
        CodeStyleManager.getInstance(project).reformatText(file, listOf(firstLine))
      }
      checkResultByText("- first\n- second\n\nText\n\n+ third\n+ fourth")
    }
  }

  fun `test keep list markers separates adjacent lists with a blank line`() = doListMarkerTest(
    UnorderedListMarker.KEEP,
    OrderedListMarker.KEEP,
    "* star\n\n- dash\n\n+ plus\n    * nested star\n\n    - nested dash",
    input = "* star\n- dash\n+ plus\n  * nested star\n  - nested dash",
  )

  fun `test force list marker merges adjacent lists without a new blank line`() = doListMarkerTest(
    UnorderedListMarker.DASH,
    OrderedListMarker.PARENTHESIS,
    "- star\n- dash\n- plus\n    - nested star\n    - nested dash\n\n1) one\n2) two",
    input = "* star\n- dash\n+ plus\n  * nested star\n  - nested dash\n\n1. one\n2) two",
  )

  fun `test force list marker merges adjacent lists`() = doListMarkerTest(
    UnorderedListMarker.ASTERISK,
    OrderedListMarker.KEEP,
    "* first\n* second\n\n* third",
    input = "* first\n* second\n\n+ third",
  )

  fun `test force dot merges ordered lists with their own numbers`() = doListMarkerTest(
    UnorderedListMarker.KEEP,
    OrderedListMarker.DOT,
    "1. one\n2. two\n3. three\n\n1. four\n2. five",
    input = "1. one\n2) two\n3. three\n\n1) four\n2) five",
  )

  fun `test force dot changes ordered lists inside a block quote`() = doListMarkerProcessorTest(
    orderedMarker = OrderedListMarker.DOT,
    input = "> 1. a\n> 2. b\n>\n> 5) c",
    expected = "> 1. a\n> 2. b\n>\n> 5. c",
  )

  fun `test force dash keeps a list with a marker in a disabled range`() {
    runWithTemporaryStyleSettings(project) { settings ->
      settings.FORMATTER_TAGS_ENABLED = true
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
      configureFromFileText(
        "tags.md",
        "* a\n\n  <!-- @formatter:off -->\n\n* b\n\n  <!-- @formatter:on -->\n\n* c\n\nText\n\n+ d",
      )
      WriteCommandAction.runWriteCommandAction(project) {
        ListMarkerPostFormatProcessor().processText(file, file.textRange, settings)
      }
      checkResultByText("* a\n\n  <!-- @formatter:off -->\n\n* b\n\n  <!-- @formatter:on -->\n\n* c\n\nText\n\n- d")
    }
  }

  fun `test force dash keeps a list with a marker on the line of a formatter off tag`() {
    runWithTemporaryStyleSettings(project) { settings ->
      settings.FORMATTER_TAGS_ENABLED = true
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
      val input = "* a\n\n* <!-- @formatter:off -->\n\n<!-- @formatter:on -->\n\nText\n\n+ d"
      configureFromFileText("tag-line.md", input)
      WriteCommandAction.runWriteCommandAction(project) {
        ListMarkerPostFormatProcessor().processText(file, file.textRange, settings)
      }
      checkResultByText(input.replace("+ d", "- d"))
    }
  }

  fun `test force dash keeps markers that would create a thematic break`() = doListMarkerProcessorTest(
    UnorderedListMarker.DASH,
    input = "+ - -\n\n* item",
    expected = "+ - -\n\n- item",
  )

  fun `test force asterisk keeps markers that would create a thematic break`() = doListMarkerProcessorTest(
    UnorderedListMarker.ASTERISK,
    input = "> + * *\n\n+ item",
    expected = "> + * *\n\n* item",
  )

  fun `test force asterisk keeps a nested marker that would create a thematic break`() = doListMarkerProcessorTest(
    UnorderedListMarker.ASTERISK,
    input = "1. + * *\n\n+ item",
    expected = "1. + * *\n\n* item",
  )

  fun `test force dash keeps the outer list when a nested list creates a thematic break`() = doListMarkerProcessorTest(
    UnorderedListMarker.DASH,
    input = "* + - -\n* item",
    expected = "* + - -\n* item",
  )

  fun `test force dash keeps markers that would create a thematic break outside the reformatted range`() {
    runWithTemporaryStyleSettings(project) { settings ->
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
      configureFromFileText("partial.md", "+ - -\n+ item")
      val secondLine = "+ - -\n".length
      WriteCommandAction.runWriteCommandAction(project) {
        ListMarkerPostFormatProcessor().processText(file, TextRange(secondLine, file.textLength), settings)
      }
      checkResultByText("+ - -\n+ item")
    }
  }

  fun `test reformat of a list that merges with the previous list returns the merged list`() {
    runWithTemporaryStyleSettings(project) { settings ->
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
      configureFromFileText("merge.md", "- a\n* b\n\nText")
      val secondList = file.children.filterIsInstance<MarkdownList>()[1]
      val result = WriteCommandAction.runWriteCommandAction<PsiElement>(project) {
        CodeStyleManager.getInstance(project).reformat(secondList)
      }
      checkResultByText("- a\n- b\n\nText")
      assertInstanceOf(result, MarkdownList::class.java)
    }
  }

  fun `test force list marker keeps a list that only touches the reformatted range`() {
    runWithTemporaryStyleSettings(project) { settings ->
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
      configureFromFileText("touch.md", "* first\n* second\n\nText")
      val listEnd = "* first\n* second".length
      WriteCommandAction.runWriteCommandAction(project) {
        ListMarkerPostFormatProcessor().processText(file, TextRange(listEnd, file.textLength), settings)
      }
      checkResultByText("* first\n* second\n\nText")
    }
  }

  fun `test list marker setting does not change code blocks`() {
    runWithTemporaryStyleSettings(project) { settings ->
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
      val text = "```\n* not a list\n1) not a list\n```"
      configureFromFileText("codeBlock.md", text)
      performReformatting(project, file)
      checkResultByText(text)
    }
  }

  fun `test force dash after a block quote that the formatter wraps`() {
    runWithTemporaryStyleSettings(project) { settings ->
      settings.getCommonSettings(MarkdownLanguage.INSTANCE).RIGHT_MARGIN = 40
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
        WRAP_TEXT_IF_LONG = true
        WRAP_TEXT_INSIDE_BLOCKQUOTES = true
        INSERT_QUOTE_ARROWS_ON_WRAP = true
        unorderedListMarker = UnorderedListMarker.DASH
      }
      configureFromFileText("quote.md", "> The first part of a long quote and the second part of it\n\n* item")
      performReformatting(project, file)
      checkResultByText("> The first part of a long quote and the\n> second part of it\n\n- item")
    }
  }

  /**
   * Runs only [ListMarkerPostFormatProcessor], because the formatter moves nested list items to their own lines.
   */
  private fun doListMarkerProcessorTest(
    unordered: UnorderedListMarker = UnorderedListMarker.KEEP,
    orderedMarker: OrderedListMarker = OrderedListMarker.KEEP,
    input: String,
    expected: String,
  ) {
    runWithTemporaryStyleSettings(project) { settings ->
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
        unorderedListMarker = unordered
        orderedListMarker = orderedMarker
      }
      configureFromFileText("listMarkers.md", input)
      WriteCommandAction.runWriteCommandAction(project) {
        ListMarkerPostFormatProcessor().processText(file, file.textRange, settings)
      }
      checkResultByText(expected)
    }
  }

  private fun doListMarkerTest(
    unordered: UnorderedListMarker,
    ordered: OrderedListMarker,
    expected: String,
    input: String = LIST_MARKERS_TEXT,
  ) {
    runWithTemporaryStyleSettings(project) { settings ->
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
        unorderedListMarker = unordered
        orderedListMarker = ordered
      }
      configureFromFileText("listMarkers.md", input)
      performReformatting(project, file)
      checkResultByText(expected)
      performReformatting(project, file)
      checkResultByText(expected)
    }
  }

  companion object {
    private val LIST_MARKERS_TEXT = """
      * star one
      * star two
          + nested plus

      1. dot one
      2. dot two

      + plus one
      + plus two

      1) paren one
      2) paren two

      * [ ] star open

      1. [x] dot done
    """.trimIndent()
  }
}
