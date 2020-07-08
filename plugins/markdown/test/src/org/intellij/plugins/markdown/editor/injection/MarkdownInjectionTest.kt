// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.injection.alias.LanguageGuesser.guessLanguageForInjection
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings

class MarkdownInjectionTest : LightPlatformCodeInsightTestCase() {
  fun testFenceWithoutLang() {
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

  fun testFenceWithLang() {
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

  fun testFenceDoesNotIgnoreLineSeparators() {
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

  fun testFenceInQuotes() {
    val content =
      """
      > class C {
      >
      >   public static void main(String[] args) {
      >
      >   }
      >
      > }
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

  fun testFenceInList() {
    val content =
      """
      |  class C {
      |  
      |    public static void main(String[] args) {
      |    
      |    }
      |    
      |  }
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

  fun testFenceWithLangWithDisabledAutoInjection() {
    val markdownSettings = MarkdownApplicationSettings.getInstance()
    val oldValue = markdownSettings.isDisableInjections
    try {
      markdownSettings.isDisableInjections = true
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
      markdownSettings.isDisableInjections = oldValue
    }
  }

  fun testFenceWithXml() {
    TestCase.assertNotNull(guessLanguageForInjection("xml"))
  }

  private fun doTest(text: String, shouldHaveInjection: Boolean) {
    configureFromFileText("test.md", text)

    TestCase.assertEquals(
      shouldHaveInjection, !file.findElementAt(editor.caretModel.offset)!!.language.isKindOf(MarkdownLanguage.INSTANCE)
    )
  }
}