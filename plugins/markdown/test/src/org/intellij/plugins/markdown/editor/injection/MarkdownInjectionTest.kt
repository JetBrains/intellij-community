// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser.guessLanguageForInjection
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.settings.MarkdownSettings

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

  fun `test fence in list`() {
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

  private fun doTest(text: String, shouldHaveInjection: Boolean) {
    configureFromFileText("test.md", text)

    assertEquals(
      shouldHaveInjection, !file.findElementAt(editor.caretModel.offset)!!.language.isKindOf(MarkdownLanguage.INSTANCE)
    )
  }
}
