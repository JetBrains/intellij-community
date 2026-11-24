// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.formatting.Block
import com.intellij.formatting.DependantSpacingImpl
import com.intellij.formatting.FormattingContext
import com.intellij.ide.DataManager
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.Language
import com.intellij.lang.LanguageFormatting
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.util.TextRange
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.intellij.plugins.markdown.editor.typeInEditor
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser.guessLanguageForInjection
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownElementTypes.MARKDOWN_TEMPLATE_DATA
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeBlock
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

@TestApplication
class MarkdownInjectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test fence with injection empty`() {
    doTest(
      """
        ```xml<caret>
        ```
      """.trimIndent(), false)
  }

  @Test
  fun `test fence without end token has no injection`() {
    //Incorrect behavior of parser, it will treat end fence as content
    //still let's fix this behavior
    doTest(
      """
        ```xml
        <<caret>```
      """.trimIndent(), false)
  }

  @Test
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

  @Test
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

  @Test
  fun `test indented fence with lang`(): Unit = timeoutRunBlocking {
    val text = """
      World

          ```java
          class C {
              void test() {}
          }
          ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
      assertNotNull(codeFence)
      assertSame(MarkdownElementTypes.CODE_FENCE, codeFence!!.node.elementType)
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("class C"))
      assertNotNull(injectedElement)
      assertEquals("class C {\n    void test() {}\n}", injectedElement!!.containingFile.text)
    }
  }

  @Test
  fun `test indented fence with blank content does not throw`(): Unit = timeoutRunBlocking {
    val text = "    ```java\n    \n    ```"
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
      assertNotNull(codeFence)
      val injectedFiles = assertDoesNotThrow {
        InjectedLanguageManager.getInstance(myFixture.project).getInjectedPsiFiles(codeFence!!)
      }
      assertNotNull(injectedFiles)
    }
  }

  @Test
  fun `test indented fence groups line separators with content`(): Unit = timeoutRunBlocking {
    val text = "    ```java\n    class C {}\n    class D {}\n    ```"
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
      assertNotNull(codeFence)
      val injectedFiles = InjectedLanguageManager.getInstance(myFixture.project).getInjectedPsiFiles(codeFence!!)
      assertNotNull(injectedFiles)
      val injectedDocument = PsiDocumentManager.getInstance(myFixture.project)
        .getDocument(injectedFiles!!.first().first.containingFile) as DocumentWindow
      assertEquals(2, injectedDocument.hostRanges.size)
    }
  }

  @Test
  fun `test blank line with an extra indent keeps the extra spaces in the injected code`(): Unit = timeoutRunBlocking {
    val text = "    ```python\n    a\n        \n    b\n    ```"
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
      assertNotNull(codeFence)
      val injectedFiles = InjectedLanguageManager.getInstance(myFixture.project).getInjectedPsiFiles(codeFence!!)
      assertNotNull(injectedFiles)
      val injectedFile = injectedFiles!!.first().first.containingFile
      // The four spaces of the fence stay out. The four spaces above them belong to the code.
      assertEquals("a\n    \nb", injectedFile.text)

      val injectedDocument = PsiDocumentManager.getInstance(myFixture.project).getDocument(injectedFile) as DocumentWindow
      val document = myFixture.editor.document
      for (range in injectedDocument.hostRanges) {
        val lineStart = document.getLineStartOffset(document.getLineNumber(range.startOffset))
        assertTrue(
          range.startOffset - lineStart >= 4,
          "A range must not hold the indent of the fence",
        )
      }
    }
  }

  @Test
  fun `test tab-indented fence with lang`(): Unit = timeoutRunBlocking {
    val text = listOf("\t```java", "\tclass C {}", "\t```").joinToString("\n")
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
      assertNotNull(codeFence)
      assertEquals("\t", MarkdownCodeFenceUtils.getIndent(codeFence!!))
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("class C"))
      assertNotNull(injectedElement)
      assertEquals("class C {}", injectedElement!!.containingFile.text)
    }
  }

  @Test
  fun `test indented tilde fence without language is a code fence`(): Unit = timeoutRunBlocking {
    val text = """
          ~~~
          class C {}
          ~~~
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
      assertNotNull(codeFence)
      assertSame(MarkdownElementTypes.CODE_FENCE, codeFence!!.node.elementType)
      assertNull(InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("class C")))
    }
  }

  @Test
  fun `test incomplete indented fence stays a code block`(): Unit = timeoutRunBlocking {
    val text = """
      World

          ```java
          class C {}
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    readAction {
      assertNotNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeBlock::class.java))
      assertNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java))
      assertNull(InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("class C")))
    }
  }

  @Test
  fun `test incomplete indented fence does not consume following paragraph`(): Unit = timeoutRunBlocking {
    val text = """
          ```java
          class C {}
      paragraph
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    readAction {
      assertNotNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeBlock::class.java))
      assertNotNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownParagraph::class.java))
      assertNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java))
    }
  }

  @Test
  fun `test indented fence in blockquote`(): Unit = timeoutRunBlocking {
    val text = """
      >
      >     ```java
      >     class C {
      >     void test() {}
      >     }
      >     ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
      assertNotNull(codeFence)
      assertSame(MarkdownElementTypes.CODE_FENCE, codeFence!!.node.elementType)
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("class C"))
      assertNotNull(injectedElement)
      assertEquals("class C {\nvoid test() {}\n}", injectedElement!!.containingFile.text)
    }
  }

  @Test
  fun `test indented fence in blockquote preserves nested indentation`(): Unit = timeoutRunBlocking {
    val text = """
      >     ```java
      >     class C {
      >         void test() {}
      >     }
      >     ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("class C"))
      assertNotNull(injectedElement)
      assertEquals("class C {\n    void test() {}\n}", injectedElement!!.containingFile.text)

      val escaper = codeFence.createLiteralTextEscaper()
      val decoded = StringBuilder()
      assertTrue(escaper.decode(escaper.relevantTextRange, decoded))
      assertEquals("class C {\n    void test() {}\n}", decoded.toString())
    }
  }

  @Test
  fun `test indented fence in list preserves nested indentation`(): Unit = timeoutRunBlocking {
    val text = """
      - item

            ```java
            class C {
                void test() {}
            }
            ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("class C"))
      assertNotNull(injectedElement)
      assertEquals("class C {\n    void test() {}\n}", injectedElement!!.containingFile.text)

      val escaper = codeFence.createLiteralTextEscaper()
      val decoded = StringBuilder()
      assertTrue(escaper.decode(escaper.relevantTextRange, decoded))
      assertEquals("class C {\n    void test() {}\n}", decoded.toString())
    }
  }

  @Test
  fun `test fence with one extra space in list strips indentation`(): Unit = timeoutRunBlocking {
    val text = "1. item\n\n    ```java\n    class C {}\n    ```"
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("class C"))
      assertNotNull(injectedElement)
      assertEquals("class C {}", injectedElement!!.containingFile.text)

      val escaper = codeFence.createLiteralTextEscaper()
      val decoded = StringBuilder()
      assertTrue(escaper.decode(escaper.relevantTextRange, decoded))
      assertEquals("class C {}", decoded.toString())
    }
  }

  @Test
  fun `test indented fence in nested containers`(): Unit = timeoutRunBlocking {
    val text = """
      > - item
      >
      >       ```java
      >       class C {}
      >       ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
      assertNotNull(codeFence)
      assertSame(MarkdownElementTypes.CODE_FENCE, codeFence!!.node.elementType)
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("class C"))
      assertNotNull(injectedElement)
      assertEquals("class C {}", injectedElement!!.containingFile.text)
    }
  }

  @Test
  fun `test updating indented fence in blockquote preserves prefixes`(): Unit = timeoutRunBlocking {
    val text = """
      >
      >     ```java
      >     class C {
      >     void test() {}
      >     }
      >     ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    withContext(Dispatchers.EDT) {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        codeFence.updateText("class C {\n    void test() {}\n}")
      }

      assertEquals(
        """
          >
          >     ```java
          >     class C {
          >         void test() {}
          >     }
          >     ```
        """.trimIndent(),
        myFixture.file.text,
      )
    }
  }

  @Test
  fun `test updating blockquoted fence with opening indentation preserves prefixes`(): Unit = timeoutRunBlocking {
    val text = ">   ```java\n>   class C {}\n>   ```"
    myFixture.configureByText("test.md", text)

    withContext(Dispatchers.EDT) {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        codeFence.updateText("class C {\n  }\n}")
      }

      assertEquals(">   ```java\n>   class C {\n>     }\n>   }\n>   ```", myFixture.file.text)
    }
  }

  @Test
  fun `test updating indented fence in list preserves prefixes`(): Unit = timeoutRunBlocking {
    val text = "- item\n\n      ```java\n      class C {}\n      ```"
    myFixture.configureByText("test.md", text)

    withContext(Dispatchers.EDT) {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        codeFence.updateText("class D {\n}")
      }

      assertEquals("- item\n\n      ```java\n      class D {\n      }\n      ```", myFixture.file.text)
    }
  }

  @Test
  fun `test updating top-level indented fence preserves indentation`(): Unit = timeoutRunBlocking {
    val text = "    ```java\n    class C {}\n    ```"
    myFixture.configureByText("test.md", text)

    withContext(Dispatchers.EDT) {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
      assertEquals("    ", MarkdownCodeFenceUtils.getIndent(codeFence))
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        codeFence.updateText("class D {\n}")
      }

      assertEquals("    ```java\n    class D {\n    }\n    ```", myFixture.file.text)
    }
  }

  @Test
  fun `test indented fence uses injected formatter`(): Unit = timeoutRunBlocking {
    val text = """
          ```java
          class C {
          void test() {
          int value=1;
          }
          }
          ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    withContext(Dispatchers.EDT) {
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("class C"))!!
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        CodeStyleManager.getInstance(myFixture.project).reformatText(injectedElement.containingFile, listOf(injectedElement.containingFile.textRange))
      }

      assertEquals(
        """
              ```java
              class C {
                  void test() {
                      int value = 1;
                  }
              }
              ```
        """.trimIndent(),
        myFixture.file.text,
      )
    }
  }

  @Test
  fun `test nested fence uses injected formatter`(): Unit = timeoutRunBlocking {
    val text = """
      - item

            ```java
            class C {
            void test() {
            int value=1;
            }
            }
            ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        CodeStyleManager.getInstance(myFixture.project).reformatText(myFixture.file, listOf(myFixture.file.textRange))
      }

      assertEquals(
        """
          - item

                ```java
                class C {
                    void test() {
                        int value = 1;
                    }
                }
                ```
        """.trimIndent(),
        myFixture.file.text,
      )
    }
  }

  @Test
  fun `test dependent spacing in nested fence uses host ranges`(): Unit = timeoutRunBlocking {
    val text = """
      - item

            ```java
            class C {
            void test() { int value=1; }
            }
            ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("class C"))!!
      val injectedFile = injectedElement.containingFile
      CodeStyle.doWithTemporarySettings(myFixture.project, CodeStyle.getSettings(myFixture.project)) { settings ->
        val javaSettings = settings.getCommonSettings(Language.findLanguageByID("JAVA")!!)
        javaSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
        javaSettings.KEEP_LINE_BREAKS = true

        val injectedModel = LanguageFormatting.INSTANCE.forContext(injectedFile)
          ?.createModel(FormattingContext.create(injectedFile, settings))
          ?: error("Java formatter is not available")
        val injectedLanguageManager = InjectedLanguageManager.getInstance(myFixture.project)
        val expectedHostRanges = injectedModel.rootBlock.getDependentSpacings()
          .flatMap { it.dependentRegionRanges }
          .map { injectedLanguageManager.injectedToHost(injectedFile, it) }
          .toSet()
        assertTrue(expectedHostRanges.isNotEmpty())

        val model = LanguageFormatting.INSTANCE.forContext(myFixture.file)
          ?.createModel(FormattingContext.create(myFixture.file, settings))
          ?: error("Markdown formatter is not available")
        val actualHostRanges = model.rootBlock.getDependentSpacings()
          .flatMap { it.dependentRegionRanges }
          .toSet()
        assertEquals(expectedHostRanges, actualHostRanges)
        assertTrue(actualHostRanges.all { codeFence.textRange.contains(it) })
      }
    }
  }

  @Test
  fun `test nested fence in blockquote partially formats content`(): Unit = timeoutRunBlocking {
    val text = """
      > ```java
      > class C {
      >
      > void test() {
      > int value=1;
      > }
      > }
      > ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        CodeStyleManager.getInstance(myFixture.project).reformatText(myFixture.file, listOf(myFixture.file.textRange))
      }

      assertEquals(
        """
          > ```java
          > class C {
          >
          > void test() {
          > int value = 1;
          > }
          > }
          > ```
        """.trimIndent(),
        myFixture.file.text,
      )
    }
  }

  @Test
  fun `test top-level indented fence with blank line formats content with multiple fragments`(): Unit = timeoutRunBlocking {
    val text = """
      |    ```java
      |    class C {
      |
      |    void test() {
      |    int value=1;
      |    }
      |    }
      |    ```
    """.trimMargin()
    myFixture.configureByText("test.md", text)

    withContext(Dispatchers.EDT) {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
      assertSame(myFixture.file, codeFence.parent)
      val injectedFiles = InjectedLanguageManager.getInstance(myFixture.project).getInjectedPsiFiles(codeFence)
      assertNotNull(injectedFiles)
      val injectedFile = injectedFiles!!.first().first.containingFile
      val injectedDocument = PsiDocumentManager.getInstance(myFixture.project).getDocument(injectedFile) as DocumentWindow
      assertTrue(injectedDocument.hostRanges.size > 1)

      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        CodeStyleManager.getInstance(myFixture.project).reformatText(myFixture.file, listOf(myFixture.file.textRange))
      }

      assertEquals(
        """
          |    ```java
          |    class C {
          |
          |        void test() {
          |            int value = 1;
          |        }
          |    }
          |    ```
        """.trimMargin(),
        myFixture.file.text,
      )
    }
  }

  @Test
  fun `test nested fence without injection remains unformatted`(): Unit = timeoutRunBlocking {
    val text = """
      - item

            ```
            class C{
            }
            ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    withContext(Dispatchers.EDT) {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
      assertNull(InjectedLanguageManager.getInstance(myFixture.project).getInjectedPsiFiles(codeFence))

      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        CodeStyleManager.getInstance(myFixture.project).reformatText(myFixture.file, listOf(myFixture.file.textRange))
      }

      assertEquals(text, myFixture.file.text)
    }
  }

  @Test
  fun `test well formatted code remains as is`(): Unit = timeoutRunBlocking {
    val text = """
      ```java
      if (policy == null) {
          throw new ResourceNotFoundException("Policy not found with id: " + policyId);
      }
      ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        CodeStyleManager.getInstance(myFixture.project).reformatText(myFixture.file, listOf(myFixture.file.textRange))
      }

      assertEquals(text, myFixture.file.text)
    }
  }

  @Test
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

    runReadActionBlocking {
      assertEquals(content, myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!.containingFile.text)
    }
  }

  @Test
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
    runReadActionBlocking {
      assertEquals(content, myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!.containingFile.text)
    }
  }

  @Test
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
    runReadActionBlocking {
      assertEquals(content, myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!.containingFile.text)
    }
  }

  @Test
  fun `test fence with lang with disabled auto injection`() {
    val markdownSettings = MarkdownSettings.getInstance()
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

  @Test
  fun `test fence with xml`() {
    assertNotNull(guessLanguageForInjection("xml"))
  }

  @Test
  fun `test injected markdown html root uses markdown template data`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("test.md", text)

    readAction {
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, text.indexOf("- Primary"))
      assertNotNull(injectedElement)

      val htmlFile = injectedElement!!.containingFile.viewProvider.getPsi(HTMLLanguage.INSTANCE)
      assertNotNull(htmlFile)
      val htmlPsiFile = htmlFile as PsiFileImpl
      assertEquals(MARKDOWN_TEMPLATE_DATA, htmlPsiFile.contentElementType)

      val htmlTree = DebugUtil.psiToString(htmlPsiFile, true, false)
      assertTrue(htmlTree.contains("MARKDOWN_OUTER_BLOCK"), htmlTree)
      assertFalse(htmlTree.contains("HtmlTag:b"), htmlTree)
      assertTrue(htmlTree.contains("HtmlTag:table"), htmlTree)
    }
  }

  @Test
  fun `test html script keeps markdown line breaks`(): Unit = timeoutRunBlocking {
    val text = """
      <script setup>
      import { useData } from 'vitepress'

      const { theme, page, frontmatter } = useData()
      </script>
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    readAction {
      val htmlFile = myFixture.file.viewProvider.getPsi(HTMLLanguage.INSTANCE)
      assertNotNull(htmlFile)
      val scriptTag = PsiTreeUtil.findChildOfType(htmlFile, XmlTag::class.java)
      assertNotNull(scriptTag)
      val scriptTagElement = scriptTag!!
      assertTrue(scriptTagElement.value.text.contains("import { useData } from 'vitepress'\n\nconst"))
      assertFalse(PsiTreeUtil.findChildrenOfType(scriptTagElement, PsiElement::class.java)
                    .any { it.node.elementType == MarkdownElementTypes.MARKDOWN_OUTER_BLOCK })
    }
  }

  /**
   * Special test for IDEA-242751
   * It checks that in case of now elements in code fence still InjectionHost
   * will return TextRange that is located inside of injection valid range
   */
  @Test
  fun `test no exceptions on reusing completion copy with emptied original injection with lang`(): Unit = timeoutRunBlocking {
    val ilm = InjectedLanguageManager.getInstance(myFixture.project)

    doTest("```xml\n<caret><\n```", true)

    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)

      CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(myFixture.project, myFixture.editor, 1)
      LookupManager.getActiveLookup(myFixture.editor)!!.hideLookup(true)

      PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
      var editor = InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.editor)
      val file = ilm.getTopLevelFile(myFixture.file)

      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        CommandProcessor.getInstance().executeCommand(myFixture.project, {
          EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_BACKSPACE, true)
        }, "", null, editor.document)
        PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
        ilm.findInjectedElementAt(file, editor.caretModel.offset)

        TypedAction.getInstance().actionPerformed(editor, '<', DataManager.getInstance().dataContext)
        PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
        assertNotNull(ilm.findInjectedElementAt(file, editor.caretModel.offset))
      }

      editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file)
      CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(myFixture.project, editor, 1)
      assertNotNull(LookupManager.getActiveLookup(editor)) // and no exceptions!
    }
  }

  @Test
  fun `test code fence escaper accepts injected range starting at opening line break`(): Unit = timeoutRunBlocking {
    val text = "```java\n    String s = \"<h1>test</h1>\";\n```"
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
      val relevantRange = codeFence.createLiteralTextEscaper().relevantTextRange.shiftRight(codeFence.textRange.startOffset)
      val injectedRange = TextRange.create(
        codeFence.textRange.startOffset + codeFence.text.indexOf('\n'),
        codeFence.textRange.startOffset + codeFence.text.lastIndexOf('\n')
      )

      assertTrue(
        relevantRange.contains(injectedRange),
        "$injectedRange should be contained in relevant range $relevantRange"
      )
    }
  }

  @Test
  fun `test code fence escaper filters fence syntax`(): Unit = timeoutRunBlocking {
    val text = "> ```java\n> class C {}\n> ```"
    myFixture.configureByText("test.md", text)

    readAction {
      val codeFence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)!!
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
  }

  @Test
  fun `test typing after replacing code fence opening line break with enter does not corrupt injected psi`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("test.md", "```java<selection>\n<caret></selection>String s = \"<h1>test</h1>\";\n```")

    withContext(Dispatchers.EDT) {
      assertDoesNotThrow {
        myFixture.typeInEditor('\n')
        myFixture.typeInEditor("class C {}")
      }
    }
  }

  @Test
  fun testEnterBetweenBracesPreservesIndentedCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        - Item 1
        - Item 2 with code:

          ```json
          {<caret>}
          ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenParenthesesPreservesConfiguredContinuationIndent(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      CodeStyle.doWithTemporarySettings(myFixture.project, CodeStyle.getSettings(myFixture.project)) { settings ->
        myFixture.configureByText(
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

        myFixture.typeInEditor('\n')
        myFixture.checkResult(
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
  }

  @Test
  fun testEnterAfterNestedOpeningBraceDoesNotReindentOuterClosingBrace(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
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

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenBracesInTopLevelCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        ```json
        {<caret>}
        ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
        """
          ```json
          {
            <caret>
          }
          ```
        """.trimIndent()
      )
    }
  }

  @Test
  fun testEnterBetweenNestedBracesPreservesCodeIndentation(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
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

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenBracketsPreservesIndentedCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        - Item with code:

          ```json
          [<caret>]
          ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenParenthesesPreservesCodeIndentation(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        ```java
        foo(<caret>)
        ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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

  @Test
  fun testEnterBetweenParenthesesPreservesConfiguredContinuationIndentInIndentedCodeFence(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      CodeStyle.doWithTemporarySettings(myFixture.project, CodeStyle.getSettings(myFixture.project)) { settings ->
        val javaLanguage = requireNotNull(Language.findLanguageByID("JAVA"))
        val indentOptions = requireNotNull(settings.getCommonSettings(javaLanguage).indentOptions)
        indentOptions.USE_TAB_CHARACTER = false
        indentOptions.INDENT_SIZE = 4
        indentOptions.CONTINUATION_INDENT_SIZE = 2

        myFixture.configureByText(
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

        myFixture.typeInEditor('\n')
        myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenBracesInJavaBlockCommentDoesNotAddCodeIndent(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        - Item with code:

          ```java
          /* {<caret>} */
          ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
        """
          - Item with code:

            ```java
            /* {
            <caret>} */
            ```
        """.trimIndent()
      )
    }
  }

  @Test
  fun testEnterBetweenBracesInBlockQuoteCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        > ```json
        > {<caret>}
        > ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
        """
          > ```json
          > {
          >   <caret>
          > }
          > ```
        """.trimIndent()
      )
    }
  }

  @Test
  fun testEnterBetweenNestedBracesInBlockQuoteCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        > ```json
        > {
        >   "a": {<caret>}
        > }
        > ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterDoesNotIndentCodeFenceTerminator(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        ```json
        {
          "deeply": "indented"<caret>
        ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
        """
          ```json
          {
            "deeply": "indented"
            <caret>
          ```
        """.trimIndent()
      )
    }
  }

  @Test
  fun testEnterBetweenBracketsInBlockQuoteCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        > ```json
        > [<caret>]
        > ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
        """
          > ```json
          > [
          >   <caret>
          > ]
          > ```
        """.trimIndent()
      )
    }
  }

  @Test
  fun testEnterBetweenBracesInOrderedListCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        1. Item with code:

           ```json
           {<caret>}
           ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenBracesInNestedListCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        - Outer item
          - Inner item with code:

            ```json
            {<caret>}
            ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenBracesInNestedBlockQuoteCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        > > ```json
        > > {<caret>}
        > > ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
        """
          > > ```json
          > > {
          > >   <caret>
          > > }
          > > ```
        """.trimIndent()
      )
    }
  }

  @Test
  fun testEnterBetweenBracesInListInsideBlockQuoteCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        > - Item with code:
        >
        >   ```json
        >   {<caret>}
        >   ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenBracesInIndentedTopLevelCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        Some text:

           ```json
           {<caret>}
           ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenBracesSurroundedByWhitespaceInIndentedCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        - Item with code:

          ```json
          {<caret> }
          ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenBracesInIndentedJavaCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        - Item with code:

          ```java
          class A {<caret>}
          ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenParenthesesInIndentedJavaCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        - Item with code:

          ```java
          foo(<caret>)
          ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  /**
   * Java registers no `enterBetweenBracesDelegate`, so `[` and `]` are not a brace pair for it and the pair is not split.
   * The new line still gets the Java continuation indent: only `EnterBetweenBracesFinalHandler` skips formatting inside a
   * code fence, the regular Enter indentation keeps using the injected language.
   */
  @Test
  fun testEnterBetweenBracketsInJavaCodeFenceIsNotABracePair(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        - Item with code:

          ```java
          int[] a = new int[<caret>];
          ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
        """
          - Item with code:

            ```java
            int[] a = new int[
                    <caret>];
            ```
        """.trimIndent()
      )
    }
  }

  @Test
  fun testEnterBetweenBracesInIndentedYamlCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        - Item with code:

          ```yaml
          key: {<caret>}
          ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenBracesWithDisabledSmartIndent(): Unit = timeoutRunBlocking {
    val codeInsightSettings = CodeInsightSettings.getInstance()
    val smartIndentOnEnter = codeInsightSettings.SMART_INDENT_ON_ENTER
    codeInsightSettings.SMART_INDENT_ON_ENTER = false
    try {
      myFixture.configureByText(
        "test.md",
        """
          - Item with code:

            ```json
            {<caret>}
            ```
        """.trimIndent()
      )

      withContext(Dispatchers.EDT) {
        myFixture.typeInEditor('\n')
        myFixture.checkResult(
          """
            - Item with code:

              ```json
              {
              <caret>}
              ```
          """.trimIndent()
        )
      }
    }
    finally {
      codeInsightSettings.SMART_INDENT_ON_ENTER = smartIndentOnEnter
    }
  }

  /**
   * `EnterAfterUnmatchedBraceHandler` inserts the missing brace.
   * A top-level fence does not need host indentation restoration.
   */
  @Test
  fun testEnterAfterUnmatchedBraceInTopLevelCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        ```json
        {
          "a": {<caret>
        }
        ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
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
  }

  @Test
  fun testEnterBetweenBracesWithFourSpaceIndentInIndentedCodeFence(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      CodeStyle.doWithTemporarySettings(myFixture.project, CodeStyle.getSettings(myFixture.project)) { settings ->
        val jsonLanguage = requireNotNull(Language.findLanguageByID("JSON"))
        val indentOptions = requireNotNull(settings.getCommonSettings(jsonLanguage).indentOptions)
        indentOptions.INDENT_SIZE = 4

        myFixture.configureByText(
          "test.md",
          """
            - Item with code:

              ```json
              {<caret>}
              ```
          """.trimIndent()
        )

        myFixture.typeInEditor('\n')
        myFixture.checkResult(
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
    }
  }

  @Test
  fun testEnterBetweenBracesWithTabIndentInIndentedCodeFence(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      CodeStyle.doWithTemporarySettings(myFixture.project, CodeStyle.getSettings(myFixture.project)) { settings ->
        val jsonLanguage = requireNotNull(Language.findLanguageByID("JSON"))
        val indentOptions = requireNotNull(settings.getCommonSettings(jsonLanguage).indentOptions)
        indentOptions.USE_TAB_CHARACTER = true
        indentOptions.TAB_SIZE = 2
        indentOptions.INDENT_SIZE = 2
        indentOptions.CONTINUATION_INDENT_SIZE = 2

        myFixture.configureByText(
          "test.md",
          """
            - Item with code:

              ```json
              {<caret>}
              ```
          """.trimIndent()
        )

        myFixture.typeInEditor('\n')
        myFixture.checkResult(
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
  }

  /**
   * No language is injected, so the braces stay inside a single Markdown token and are not split.
   * The new line gets the Markdown indent (4) on top of the code fence indent, since there is no injected language to ask.
   */
  @Test
  fun testEnterBetweenBracesInCodeFenceWithoutInjection(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
        - Item with code:

          ```foobar
          {<caret>}
          ```
      """.trimIndent()
    )

    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(
        """
          - Item with code:

            ```foobar
            {
                <caret>}
            ```
        """.trimIndent()
      )
    }
  }

  @Test
  fun `test blank line in quoted fence is not a separate injection range`(): Unit = timeoutRunBlocking {
    val text = """
      > ```shell
      > pwd
      >
      > echo done
      > ```
    """.trimIndent()
    myFixture.configureByText("test.md", text)

    readAction {
      val contentStart = text.indexOf("pwd")
      val injectedElement = InjectedLanguageManager.getInstance(myFixture.project).findInjectedElementAt(myFixture.file, contentStart)
      assertNotNull(injectedElement)
      val injectedDocument = PsiDocumentManager.getInstance(myFixture.project).getDocument(injectedElement!!.containingFile) as DocumentWindow

      assertTrue(injectedDocument.hostRanges.all { myFixture.editor.document.getText(TextRange.create(it)).isNotBlank() })
      assertEquals(
        """
          pwd

          echo done
        """.trimIndent(),
        injectedElement.containingFile.text
      )
    }
  }

  private fun doTest(text: String, shouldHaveInjection: Boolean) {
    myFixture.configureByText("test.md", text)

    runReadActionBlocking {
      assertEquals(
        shouldHaveInjection, !myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!.language.isKindOf(MarkdownLanguage.INSTANCE)
      )
    }
  }

  private fun Block.getDependentSpacings(): List<DependantSpacingImpl> {
    val children = subBlocks
    val directSpacings = children.zipWithNext().mapNotNull { [child1, child2] ->
      val spacing = getSpacing(child1, child2)
      if (spacing !is DependantSpacingImpl) return@mapNotNull null
      spacing
    }
    return directSpacings + children.flatMap { it.getDependentSpacings() }
  }
}
