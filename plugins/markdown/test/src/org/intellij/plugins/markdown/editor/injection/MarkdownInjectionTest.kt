// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser.guessLanguageForInjection
import org.intellij.plugins.markdown.lang.MarkdownElementTypes.MARKDOWN_TEMPLATE_DATA
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.junit.jupiter.api.assertDoesNotThrow

class MarkdownInjectionTest : LightPlatformCodeInsightTestCase() {
  fun `test fence with injection empty`() {
    doTest(
      """
        ```xml<caret>
        ```
      """.trimIndent(), false)
  }

  fun `test fence without end token has no injection`() {
    //Incorrect behavior of parser, it will treat end fence as content
    //still let's fix this behavior
    doTest(
      """
        ```xml
        <<caret>```
      """.trimIndent(), false)
  }

  fun `test fence without lang`() {
    doTest(
      """
        ```
        paragraph
        <caret>
        other paragraph
        }
        ```
      """.trimIndent(), false)
  }

  fun `test fence with lang`() {
    doTest(
      """
        ```text
        paragraph
        <caret>
        other paragraph
        }
        ```
      """.trimIndent(), true)
  }

  fun `test fence does not ignore line separators`() {
    val content =
      """
      class C {
      
        public static void main(String[] args) {
      
        }
      
      }
      """.trimIndent()

    val text =
      """
      ```text
      class C {
      
        public static void ma<caret>in(String[] args) {
      
        }
      
      }
      ```
      """.trimIndent()

    doTest(text, true)

    assertEquals(content, file.findElementAt(editor.caretModel.offset)!!.containingFile.text)
  }

  fun `test fence in quotes`() {
    val content =
      """
      class C {

        public static void main(String[] args) {

        }

      }
      """.trimIndent()
    val text =
      """
      > ```text
      > class C {
      >
      >   public static void ma<caret>in(String[] args) {
      >
      >   }
      >
      > }
      > ```
    """.trimIndent()
    doTest(text, true)
    assertEquals(content, file.findElementAt(editor.caretModel.offset)!!.containingFile.text)
  }

  fun `test fence in list`() {
    val content =
      """
      |class C {
      |
      |  public static void main(String[] args) {
      |  
      |  }
      |  
      |}
      """.trimMargin()
    val text =
      """
      * ```text
        class C {
        
          public static void ma<caret>in(String[] args) {
          
          }
          
        }
        ```
      """.trimIndent()
    doTest(text, true)
    assertEquals(content, file.findElementAt(editor.caretModel.offset)!!.containingFile.text)
  }

  fun `test fence with lang with disabled auto injection`() {
    val markdownSettings = MarkdownSettings.getInstance(project)
    val oldValue = markdownSettings.areInjectionsEnabled
    try {
      markdownSettings.areInjectionsEnabled = false
      doTest(
        """
        ```text
        paragraph
        <caret>
        other paragraph
        }
        ```
        """.trimIndent(), false)
    }
    finally {
      markdownSettings.areInjectionsEnabled = oldValue
    }
  }

  fun `test fence with xml`() {
    assertNotNull(guessLanguageForInjection("xml"))
  }

  fun `test injected markdown html root uses markdown template data`() {
    val text = """
      ```markdown
      ## Goals
      - Primary outcomes the feature must deliver.
      Inline <b>HTML</b> stays markdown content.

      <table>

      **bold**

      </table>
      ```
    """.trimIndent()
    configureFromFileText("test.md", text)

    val injectedElement = InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, text.indexOf("- Primary"))
    assertNotNull(injectedElement)

    val htmlFile = injectedElement!!.containingFile.viewProvider.getPsi(HTMLLanguage.INSTANCE)
    assertNotNull(htmlFile)
    val htmlPsiFile = htmlFile as PsiFileImpl
    assertEquals(MARKDOWN_TEMPLATE_DATA, htmlPsiFile.contentElementType)

    val htmlTree = DebugUtil.psiToString(htmlPsiFile, true, false)
    assertTrue(htmlTree, htmlTree.contains("MARKDOWN_OUTER_BLOCK"))
    assertFalse(htmlTree, htmlTree.contains("HtmlTag:b"))
    assertTrue(htmlTree, htmlTree.contains("HtmlTag:table"))
  }

  /**
   * Special test for IDEA-242751
   * It checks that in case of now elements in code fence still InjectionHost
   * will return TextRange that is located inside of injection valid range
   */
  fun `test no exceptions on reusing completion copy with emptied original injection with lang`() {
    val ilm = InjectedLanguageManager.getInstance(project)

    doTest("```xml\n<caret><\n```", true)

    caretRight()

    CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor, 1)
    LookupManager.getActiveLookup(editor)!!.hideLookup(true)

    bringRealEditorBack()

    WriteCommandAction.runWriteCommandAction(project) {
      backspace()
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      ilm.findInjectedElementAt(file, editor.caretModel.offset)

      type('<')
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      assertNotNull(ilm.findInjectedElementAt(file, editor.caretModel.offset))
    }

    setupEditorForInjectedLanguage()
    CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor, 1)
    assertNotNull(LookupManager.getActiveLookup(editor)) // and no exceptions!
  }

  fun `test code fence escaper accepts injected range starting at opening line break`() {
    val text = "```java\n    String s = \"<h1>test</h1>\";\n```"
    configureFromFileText("test.md", text)

    val codeFence = PsiTreeUtil.findChildOfType(file, MarkdownCodeFence::class.java)!!
    val relevantRange = codeFence.createLiteralTextEscaper().relevantTextRange.shiftRight(codeFence.textRange.startOffset)
    val injectedRange = TextRange.create(
      codeFence.textRange.startOffset + codeFence.text.indexOf('\n'),
      codeFence.textRange.startOffset + codeFence.text.lastIndexOf('\n')
    )

    assertTrue(
      "$injectedRange should be contained in relevant range $relevantRange",
      relevantRange.contains(injectedRange)
    )
  }

  fun `test code fence escaper filters fence syntax`() {
    val text = "> ```java\n> class C {}\n> ```"
    configureFromFileText("test.md", text)

    val codeFence = PsiTreeUtil.findChildOfType(file, MarkdownCodeFence::class.java)!!
    val escaper = codeFence.createLiteralTextEscaper()
    val range = escaper.relevantTextRange
    val decoded = StringBuilder()

    assertTrue(escaper.decode(range, decoded))
    assertEquals("class C {}", decoded.toString())

    val contentStart = codeFence.text.indexOf("class")
    assertEquals(contentStart, escaper.getOffsetInHost(0, range))
    assertEquals(contentStart + decoded.length, escaper.getOffsetInHost(decoded.length, range))
    assertEquals(-1, escaper.getOffsetInHost(decoded.length + 1, range))
  }

  fun `test typing after replacing code fence opening line break with enter does not corrupt injected psi`() {
    configureFromFileText("test.md", "```java<selection>\n<caret></selection>String s = \"<h1>test</h1>\";\n```")

    assertDoesNotThrow {
      type('\n')
      type("class C {}")
    }
  }

  fun testEnterBetweenBracesPreservesIndentedCodeFence() {
    configureFromFileText(
      "test.md",
      """
        - Item 1
        - Item 2 with code:

          ```json
          {<caret>}
          ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - Item 1
        - Item 2 with code:

          ```json
          {
            <caret>
          }
          ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenParenthesesPreservesConfiguredContinuationIndent() {
    CodeStyle.doWithTemporarySettings(project, CodeStyle.getSettings(project)) { settings ->
      configureFromFileText(
        "test.md",
        """
          ```java
          foo(<caret>)
          ```
        """.trimIndent()
      )

      val javaLanguage = requireNotNull(Language.findLanguageByID("JAVA"))
      val indentOptions = requireNotNull(settings.getCommonSettings(javaLanguage).indentOptions)
      indentOptions.USE_TAB_CHARACTER = false
      indentOptions.INDENT_SIZE = 4
      indentOptions.CONTINUATION_INDENT_SIZE = 2

      type('\n')
      checkResultByText(
        """
          ```java
          foo(
            <caret>
          )
          ```
        """.trimIndent()
      )
    }
  }

  fun testEnterAfterNestedOpeningBraceDoesNotReindentOuterClosingBrace() {
    configureFromFileText(
      "test.md",
      """
        - item

          ```json
          {
            "a": {<caret>
          }
          ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - item

          ```json
          {
            "a": {
              <caret>
            }
          }
          ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesInTopLevelCodeFence() {
    configureFromFileText(
      "test.md",
      """
        ```json
        {<caret>}
        ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        ```json
        {
          <caret>
        }
        ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenNestedBracesPreservesCodeIndentation() {
    configureFromFileText(
      "test.md",
      """
        - Item with code:

          ```json
          {
            "a": {<caret>}
          }
          ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - Item with code:

          ```json
          {
            "a": {
              <caret>
            }
          }
          ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracketsPreservesIndentedCodeFence() {
    configureFromFileText(
      "test.md",
      """
        - Item with code:

          ```json
          [<caret>]
          ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - Item with code:

          ```json
          [
            <caret>
          ]
          ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenParenthesesPreservesCodeIndentation() {
    configureFromFileText(
      "test.md",
      """
        ```java
        foo(<caret>)
        ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        ```java
        foo(
                <caret>
        )
        ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenParenthesesPreservesConfiguredContinuationIndentInIndentedCodeFence() {
    CodeStyle.doWithTemporarySettings(project, CodeStyle.getSettings(project)) { settings ->
      val javaLanguage = requireNotNull(Language.findLanguageByID("JAVA"))
      val indentOptions = requireNotNull(settings.getCommonSettings(javaLanguage).indentOptions)
      indentOptions.USE_TAB_CHARACTER = false
      indentOptions.INDENT_SIZE = 4
      indentOptions.CONTINUATION_INDENT_SIZE = 2

      configureFromFileText(
        "test.md",
        """
          - Item 1
          - Item 2
          - Item 3 with code:

            ```java
            foo(<caret>)
            ```
        """.trimIndent()
      )

      type('\n')
      checkResultByText(
        """
          - Item 1
          - Item 2
          - Item 3 with code:

            ```java
            foo(
              <caret>
            )
            ```
        """.trimIndent()
      )
    }
  }

  fun testEnterBetweenBracesInJavaBlockCommentDoesNotAddCodeIndent() {
    configureFromFileText(
      "test.md",
      """
        - Item with code:

          ```java
          /* {<caret>} */
          ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - Item with code:

          ```java
          /* {
          <caret>} */
          ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesInBlockQuoteCodeFence() {
    configureFromFileText(
      "test.md",
      """
        > ```json
        > {<caret>}
        > ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        > ```json
        > {
        >   <caret>
        > }
        > ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenNestedBracesInBlockQuoteCodeFence() {
    configureFromFileText(
      "test.md",
      """
        > ```json
        > {
        >   "a": {<caret>}
        > }
        > ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        > ```json
        > {
        >   "a": {
        >     <caret>
        >   }
        > }
        > ```
      """.trimIndent()
    )
  }

  fun testEnterDoesNotIndentCodeFenceTerminator() {
    configureFromFileText(
      "test.md",
      """
        ```json
        {
          "deeply": "indented"<caret>
        ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        ```json
        {
          "deeply": "indented"
          <caret>
        ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracketsInBlockQuoteCodeFence() {
    configureFromFileText(
      "test.md",
      """
        > ```json
        > [<caret>]
        > ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        > ```json
        > [
        >   <caret>
        > ]
        > ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesInOrderedListCodeFence() {
    configureFromFileText(
      "test.md",
      """
        1. Item with code:

           ```json
           {<caret>}
           ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        1. Item with code:

           ```json
           {
             <caret>
           }
           ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesInNestedListCodeFence() {
    configureFromFileText(
      "test.md",
      """
        - Outer item
          - Inner item with code:

            ```json
            {<caret>}
            ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - Outer item
          - Inner item with code:

            ```json
            {
              <caret>
            }
            ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesInNestedBlockQuoteCodeFence() {
    configureFromFileText(
      "test.md",
      """
        > > ```json
        > > {<caret>}
        > > ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        > > ```json
        > > {
        > >   <caret>
        > > }
        > > ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesInListInsideBlockQuoteCodeFence() {
    configureFromFileText(
      "test.md",
      """
        > - Item with code:
        >
        >   ```json
        >   {<caret>}
        >   ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        > - Item with code:
        >
        >   ```json
        >   {
        >     <caret>
        >   }
        >   ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesInIndentedTopLevelCodeFence() {
    configureFromFileText(
      "test.md",
      """
        Some text:

           ```json
           {<caret>}
           ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        Some text:

           ```json
           {
             <caret>
           }
           ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesSurroundedByWhitespaceInIndentedCodeFence() {
    configureFromFileText(
      "test.md",
      """
        - Item with code:

          ```json
          {<caret> }
          ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - Item with code:

          ```json
          {
            <caret>
          }
          ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesInIndentedJavaCodeFence() {
    configureFromFileText(
      "test.md",
      """
        - Item with code:

          ```java
          class A {<caret>}
          ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - Item with code:

          ```java
          class A {
              <caret>
          }
          ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenParenthesesInIndentedJavaCodeFence() {
    configureFromFileText(
      "test.md",
      """
        - Item with code:

          ```java
          foo(<caret>)
          ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - Item with code:

          ```java
          foo(
                  <caret>
          )
          ```
      """.trimIndent()
    )
  }

  /**
   * Java registers no `enterBetweenBracesDelegate`, so `[` and `]` are not a brace pair for it and the pair is not split.
   * The new line still gets the Java continuation indent: only `EnterBetweenBracesFinalHandler` skips formatting inside a
   * code fence, the regular Enter indentation keeps using the injected language.
   */
  fun testEnterBetweenBracketsInJavaCodeFenceIsNotABracePair() {
    configureFromFileText(
      "test.md",
      """
        - Item with code:

          ```java
          int[] a = new int[<caret>];
          ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - Item with code:

          ```java
          int[] a = new int[
                  <caret>];
          ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesInIndentedYamlCodeFence() {
    configureFromFileText(
      "test.md",
      """
        - Item with code:

          ```yaml
          key: {<caret>}
          ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - Item with code:

          ```yaml
          key: {
            <caret>
          }
          ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesWithDisabledSmartIndent() {
    val codeInsightSettings = CodeInsightSettings.getInstance()
    val smartIndentOnEnter = codeInsightSettings.SMART_INDENT_ON_ENTER
    codeInsightSettings.SMART_INDENT_ON_ENTER = false
    try {
      configureFromFileText(
        "test.md",
        """
          - Item with code:

            ```json
            {<caret>}
            ```
        """.trimIndent()
      )

      type('\n')
      checkResultByText(
        """
          - Item with code:

            ```json
            {
            <caret>}
            ```
        """.trimIndent()
      )
    }
    finally {
      codeInsightSettings.SMART_INDENT_ON_ENTER = smartIndentOnEnter
    }
  }

  /**
   * Enter after an unmatched opening brace is handled by `EnterAfterUnmatchedBraceHandler`, which inserts the missing
   * brace. In a top level code fence that works; the same content in an indented code fence currently fails.
   */
  fun testEnterAfterUnmatchedBraceInTopLevelCodeFence() {
    configureFromFileText(
      "test.md",
      """
        ```json
        {
          "a": {<caret>
        }
        ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        ```json
        {
          "a": {
            <caret>
          }
        }
        ```
      """.trimIndent()
    )
  }

  fun testEnterBetweenBracesWithTabIndentInIndentedCodeFence() {
    CodeStyle.doWithTemporarySettings(project, CodeStyle.getSettings(project)) { settings ->
      val jsonLanguage = requireNotNull(Language.findLanguageByID("JSON"))
      val indentOptions = requireNotNull(settings.getCommonSettings(jsonLanguage).indentOptions)
      indentOptions.USE_TAB_CHARACTER = true
      indentOptions.TAB_SIZE = 2
      indentOptions.INDENT_SIZE = 2
      indentOptions.CONTINUATION_INDENT_SIZE = 2

      configureFromFileText(
        "test.md",
        """
          - Item with code:

            ```json
            {<caret>}
            ```
        """.trimIndent()
      )

      type('\n')
      checkResultByText(
        """
          - Item with code:

            ```json
            {
            ${'\t'}<caret>
            }
            ```
        """.trimIndent()
      )
    }
  }

  /**
   * No language is injected, so the braces stay inside a single Markdown token and are not split.
   * The new line gets the Markdown indent (4) on top of the code fence indent, since there is no injected language to ask.
   */
  fun testEnterBetweenBracesInCodeFenceWithoutInjection() {
    configureFromFileText(
      "test.md",
      """
        - Item with code:

          ```foobar
          {<caret>}
          ```
      """.trimIndent()
    )

    type('\n')
    checkResultByText(
      """
        - Item with code:

          ```foobar
          {
              <caret>}
          ```
      """.trimIndent()
    )
  }

  fun `test blank line in quoted fence is not a separate injection range`() {
    val text = """
      > ```shell
      > pwd
      >
      > echo done
      > ```
    """.trimIndent()
    configureFromFileText("test.md", text)

    val contentStart = text.indexOf("pwd")
    val injectedElement = InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, contentStart)
    assertNotNull(injectedElement)
    val injectedDocument = PsiDocumentManager.getInstance(project).getDocument(injectedElement!!.containingFile) as DocumentWindow

    assertTrue(injectedDocument.hostRanges.all { editor.document.getText(TextRange.create(it)).isNotBlank() })
    assertEquals(
      """
        pwd

        echo done
      """.trimIndent(),
      injectedElement.containingFile.text
    )
  }

  private fun doTest(text: String, shouldHaveInjection: Boolean) {
    configureFromFileText("test.md", text)

    assertEquals(
      shouldHaveInjection, !file.findElementAt(editor.caretModel.offset)!!.language.isKindOf(MarkdownLanguage.INSTANCE)
    )
  }
}
