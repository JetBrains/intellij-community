// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.formatter

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.performReformatting
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.runWithTemporaryStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.formatter.ListMarkerPostFormatProcessor
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.formatter.settings.OrderedListMarker
import org.intellij.plugins.markdown.lang.formatter.settings.UnorderedListMarker
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownListMarkerFormatterTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test keep existing list markers`(): Unit = doListMarkerTest(
    UnorderedListMarker.KEEP,
    OrderedListMarker.KEEP,
    LIST_MARKERS_TEXT,
  )

  @Test
  fun `test force dash and parenthesis list markers`(): Unit = doListMarkerTest(
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

  @Test
  fun `test force asterisk and dot list markers`(): Unit = doListMarkerTest(
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

  @Test
  fun `test force plus list marker keeps ordered list markers`(): Unit = doListMarkerTest(
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

  @Test
  fun `test force list marker changes the whole list in the reformatted range`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
        myFixture.configureByText("range.md", "* first\n* second\n\nText\n\n+ third\n+ fourth")
        val firstLine = TextRange(0, "* first".length)
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          CodeStyleManager.getInstance(myFixture.project).reformatText(myFixture.file, listOf(firstLine))
        }
        myFixture.checkResult("- first\n- second\n\nText\n\n+ third\n+ fourth")
      }
    }
  }

  @Test
  fun `test keep list markers separates adjacent lists with a blank line`(): Unit = doListMarkerTest(
    UnorderedListMarker.KEEP,
    OrderedListMarker.KEEP,
    "* star\n\n- dash\n\n+ plus\n    * nested star\n\n    - nested dash",
    input = "* star\n- dash\n+ plus\n  * nested star\n  - nested dash",
  )

  @Test
  fun `test force list marker merges adjacent lists without a new blank line`(): Unit = doListMarkerTest(
    UnorderedListMarker.DASH,
    OrderedListMarker.PARENTHESIS,
    "- star\n- dash\n- plus\n    - nested star\n    - nested dash\n\n1) one\n2) two",
    input = "* star\n- dash\n+ plus\n  * nested star\n  - nested dash\n\n1. one\n2) two",
  )

  @Test
  fun `test force list marker merges adjacent lists`(): Unit = doListMarkerTest(
    UnorderedListMarker.ASTERISK,
    OrderedListMarker.KEEP,
    "* first\n* second\n\n* third",
    input = "* first\n* second\n\n+ third",
  )

  @Test
  fun `test force dot merges ordered lists with their own numbers`(): Unit = doListMarkerTest(
    UnorderedListMarker.KEEP,
    OrderedListMarker.DOT,
    "1. one\n2. two\n3. three\n\n1. four\n2. five",
    input = "1. one\n2) two\n3. three\n\n1) four\n2) five",
  )

  @Test
  fun `test force dot changes ordered lists inside a block quote`(): Unit = doListMarkerProcessorTest(
    orderedMarker = OrderedListMarker.DOT,
    input = "> 1. a\n> 2. b\n>\n> 5) c",
    expected = "> 1. a\n> 2. b\n>\n> 5. c",
  )

  @Test
  fun `test force dash keeps a list with a marker in a disabled range`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.FORMATTER_TAGS_ENABLED = true
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
        myFixture.configureByText(
          "tags.md",
          "* a\n\n  <!-- @formatter:off -->\n\n* b\n\n  <!-- @formatter:on -->\n\n* c\n\nText\n\n+ d",
        )
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          ListMarkerPostFormatProcessor().processText(myFixture.file, myFixture.file.textRange, settings)
        }
        myFixture.checkResult("* a\n\n  <!-- @formatter:off -->\n\n* b\n\n  <!-- @formatter:on -->\n\n* c\n\nText\n\n- d")
      }
    }
  }

  @Test
  fun `test force dash keeps a list with a marker on the line of a formatter off tag`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.FORMATTER_TAGS_ENABLED = true
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
        val input = "* a\n\n* <!-- @formatter:off -->\n\n<!-- @formatter:on -->\n\nText\n\n+ d"
        myFixture.configureByText("tag-line.md", input)
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          ListMarkerPostFormatProcessor().processText(myFixture.file, myFixture.file.textRange, settings)
        }
        myFixture.checkResult(input.replace("+ d", "- d"))
      }
    }
  }

  @Test
  fun `test force dash keeps markers that would create a thematic break`(): Unit = doListMarkerProcessorTest(
    UnorderedListMarker.DASH,
    input = "+ - -\n\n* item",
    expected = "+ - -\n\n- item",
  )

  @Test
  fun `test force asterisk keeps markers that would create a thematic break`(): Unit = doListMarkerProcessorTest(
    UnorderedListMarker.ASTERISK,
    input = "> + * *\n\n+ item",
    expected = "> + * *\n\n* item",
  )

  @Test
  fun `test force asterisk keeps a nested marker that would create a thematic break`(): Unit = doListMarkerProcessorTest(
    UnorderedListMarker.ASTERISK,
    input = "1. + * *\n\n+ item",
    expected = "1. + * *\n\n* item",
  )

  @Test
  fun `test force dash keeps the outer list when a nested list creates a thematic break`(): Unit = doListMarkerProcessorTest(
    UnorderedListMarker.DASH,
    input = "* + - -\n* item",
    expected = "* + - -\n* item",
  )

  @Test
  fun `test force dash keeps markers that would create a thematic break outside the reformatted range`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
        myFixture.configureByText("partial.md", "+ - -\n+ item")
        val secondLine = "+ - -\n".length
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          ListMarkerPostFormatProcessor().processText(myFixture.file, TextRange(secondLine, myFixture.file.textLength), settings)
        }
        myFixture.checkResult("+ - -\n+ item")
      }
    }
  }

  @Test
  fun `test reformat of a list that merges with the previous list returns the merged list`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
        myFixture.configureByText("merge.md", "- a\n* b\n\nText")
        val secondList = myFixture.file.children.filterIsInstance<MarkdownList>()[1]
        val result = WriteCommandAction.runWriteCommandAction<PsiElement>(myFixture.project) {
          CodeStyleManager.getInstance(myFixture.project).reformat(secondList)
        }
        myFixture.checkResult("- a\n- b\n\nText")
        assertInstanceOf(MarkdownList::class.java, result)
      }
    }
  }

  @Test
  fun `test force list marker keeps a list that only touches the reformatted range`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
        myFixture.configureByText("touch.md", "* first\n* second\n\nText")
        val listEnd = "* first\n* second".length
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          ListMarkerPostFormatProcessor().processText(myFixture.file, TextRange(listEnd, myFixture.file.textLength), settings)
        }
        myFixture.checkResult("* first\n* second\n\nText")
      }
    }
  }

  @Test
  fun `test list marker setting does not change code blocks`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).unorderedListMarker = UnorderedListMarker.DASH
        val text = "```\n* not a list\n1) not a list\n```"
        myFixture.configureByText("codeBlock.md", text)
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResult(text)
      }
    }
  }

  @Test
  fun `test force dash after a block quote that the formatter wraps`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.getCommonSettings(MarkdownLanguage.INSTANCE).RIGHT_MARGIN = 40
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
          WRAP_TEXT_IF_LONG = true
          WRAP_TEXT_INSIDE_BLOCKQUOTES = true
          INSERT_QUOTE_ARROWS_ON_WRAP = true
          unorderedListMarker = UnorderedListMarker.DASH
        }
        myFixture.configureByText("quote.md", "> The first part of a long quote and the second part of it\n\n* item")
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResult("> The first part of a long quote and the\n> second part of it\n\n- item")
      }
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
  ): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
          unorderedListMarker = unordered
          orderedListMarker = orderedMarker
        }
        myFixture.configureByText("listMarkers.md", input)
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          ListMarkerPostFormatProcessor().processText(myFixture.file, myFixture.file.textRange, settings)
        }
        myFixture.checkResult(expected)
      }
    }
  }

  private fun doListMarkerTest(
    unordered: UnorderedListMarker,
    ordered: OrderedListMarker,
    expected: String,
    input: String = LIST_MARKERS_TEXT,
  ): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
          unorderedListMarker = unordered
          orderedListMarker = ordered
        }
        myFixture.configureByText("listMarkers.md", input)
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResult(expected)
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResult(expected)
      }
    }
  }

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

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
