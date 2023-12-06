// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.injection

import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.injection.Injectable
import junit.framework.TestCase
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
abstract class KotlinTrimmedInjectionTestBase : AbstractInjectionTest() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("kotlin/text/trims.kt", """
            package kotlin.text
            
            public fun String.trimIndent(): String = this
            public fun String.trimMargin(marginPrefix: String = "|"): String = this
        """.trimIndent())
    }

    fun testTrimIndentInJavaAnnotation() {
        doInjectionPresentTest(
            """
                import kotlin.text.trimIndent
    
                fun bar(a: Int) { 
                    Test.foo(""${'"'}
                    some trim indented code
                        is written here
                    no spaces before each line should appear<caret>
                    ""${'"'}.trimIndent())
                }
                """,
            javaText =
            """
                import org.intellij.lang.annotations.Language;
    
                public class Test {
                    public static void foo(@Language("HTML") String str) {}
                }
                """,
            languageId = HTMLLanguage.INSTANCE.id,
            unInjectShouldBePresent = false,
            shreds = null,
            injectedText = "some trim indented code\n" +
                    "    is written here\n" +
                    "no spaces before each line should appear"
        )
    }

    fun testTrimMarginInJavaAnnotation() {
        doInjectionPresentTest(
            """
                import kotlin.text.trimIndent
    
                fun bar(a: Int) { 
                    Test.foo(""${'"'}
                    > <html><caret>
                    > <body>
                    >   <div>
                    >       <h1>
                    >       ${"\$"}a
                    >       </h1>
                    >  </div>
                    > </body>
                    ></head>
                ""${'"'}.trimMargin(">"))
                }
                """,
            javaText =
            """
                import org.intellij.lang.annotations.Language;
    
                public class Test {
                    public static void foo(@Language("HTML") String str) {}
                }
                """,
            languageId = HTMLLanguage.INSTANCE.id,
            unInjectShouldBePresent = false,
            shreds = null,
            injectedText = " <html>\n" +
                    " <body>\n" +
                    "   <div>\n" +
                    "       <h1>\n" +
                    "       a\n" +
                    "       </h1>\n" +
                    "  </div>\n" +
                    " </body>\n" +
                    "</head>"
        )
    }

    fun testTrimIndentInKotlinAnnotation() {
        doInjectionPresentTest(
            mkFoo(
                """
                        <html>
                            <body>
                                        
                            </body>
                        </html><caret>
                    """,
                "HTML"
            ),
            languageId = HTMLLanguage.INSTANCE.id,
            unInjectShouldBePresent = false,
            shreds = null,
            injectedText = "<html>\n" +
                    "    <body>\n" +
                    "                \n" +
                    "    </body>\n" +
                    "</html>"
        )
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testTempInjection() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", """
                import kotlin.text.trimIndent
                
                fun foo() {
                    val toInject = ""${'"'}
                        <html>
                            <body><caret>
                                        
                            </body>
                        </html>
                    ""${'"'}.trimIndent()
                }
            """.trimIndent()
            )

            InjectLanguageAction.invokeImpl(project, editor, file, Injectable.fromLanguage(Language.findLanguageByID("HTML")))
            myFixture.checkResult(
                """
                import kotlin.text.trimIndent
                
                fun foo() {
                    val toInject = ""${'"'}
                        <html>
                            <body><caret>
                                        
                            </body>
                        </html>
                    ""${'"'}.trimIndent()
                }
            """.trimIndent()
            )

            TestCase.assertEquals(
                """
            <html>
                <body>
                            
                </body>
            </html>
        """.trimIndent(), myInjectionFixture.injectedElement?.containingFile?.text
            )

            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()
            TestCase.assertEquals(
                "<html>\n" +
                        "    <body>\n" +
                        "                \n" +
                        "    </body>\n" +
                        "</html>", fragmentEditorFixture.file.text
            )

            fragmentEditorFixture.type('\n')
            fragmentEditorFixture.type("asss")
            fragmentEditorFixture.type("s")

            myFixture.checkResult(
                "Foo.kt", """
                import kotlin.text.trimIndent

                fun foo() {
                    val toInject = ""${'"'}
                        <html>
                            <body>
                            assss

                            </body>
                        </html>
                    ""${'"'}.trimIndent()
                }
            """.trimIndent(), true
            )

            TestCase.assertEquals(
                "<html>\n" +
                        "    <body>\n" +
                        "    assss\n" +
                        "                \n" +
                        "    </body>\n" +
                        "</html>", fragmentEditorFixture.file.text
            )
        }
    }


    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testNewLineInFragmentEditor() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(
                    """
                        <html>
                            <body><caret>
                                        
                            </body>
                        </html>
                    """,
                    "HTML"
                )
            )

            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()
            val injectedFile = fragmentEditorFixture.file

            TestCase.assertEquals(
                "<html>\n" +
                        "    <body>\n" +
                        "                \n" +
                        "    </body>\n" +
                        "</html>", injectedFile.text
            )

            fragmentEditorFixture.type('\n')
            fragmentEditorFixture.type("asss")
            fragmentEditorFixture.type("s")

            myFixture.checkResult(
                "Foo.kt",
                mkFoo(
                    """
                        <html>
                            <body>
                            assss
                
                            </body>
                        </html>
                    """,
                    "HTML"
                ), true
            )

            TestCase.assertEquals(
                "<html>\n" +
                        "    <body>\n" +
                        "    assss\n" +
                        "                \n" +
                        "    </body>\n" +
                        "</html>", injectedFile.text
            )
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testPutTagOnNewLineInFragmentEditor() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(
                    """
                        <html>
                            <body><caret></body>
                        </html>
                    """,
                    "HTML"
                )
            )

            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()
            fragmentEditorFixture.type('\n')

            myFixture.checkResult(
                "Foo.kt",
                mkFoo(
                    """
                        <html>
                            <body>
                
                            </body>
                        </html>
                    """,
                    "HTML"
                ), true
            )
        }
    }


    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testRemoveLineBreak() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(
                    """
                            <html>
                                <body>
                                <caret></body>
                            </html>
                        """, "HTML"
                )
            )

            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)

            myFixture.checkResult(
                "Foo.kt",
                mkFoo(
                    """
                            <html>
                                <body></body>
                            </html>
                        """,
                    "HTML"
                ), true
            )
        }
    }

    fun mkFoo(literalText: String, lang: String): String {
        return """
                import kotlin.text.trimIndent
                import org.intellij.lang.annotations.Language;

                fun foo(@Language("$lang") html: String) {}

                fun bar(a: Int) {
                    foo(
                        ""${'"'}$literalText""${'"'}.trimIndent())
                }
                """.trimIndent()
    }


    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testNewLineBetweenBodies() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(
                    """
                        <html>
                            <body><caret></body>
                        </html>
                    """,
                    "HTML"
                )
            )

            myFixture.type("\n")
            myFixture.checkResult(
                mkFoo(
                    """
                        <html>
                            <body>
                            
                            </body>
                        </html>
                    """, "HTML"
                )
            )
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testOneLineTrimIndent() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(""" <caret>""", "HTML")
            )

            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

            myFixture.checkResult(
                "Foo.kt", mkFoo(
                    """
                """, "HTML"
                ), true
            )
        }
    }
    
    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testOneLineTrimIndentAndSpaces() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo("""<caret><html></html>""", "HTML")
            )

            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()
            fragmentEditorFixture.type(" ")
            fragmentEditorFixture.type(" ")
            fragmentEditorFixture.type(" ")
            val injectedFile = fragmentEditorFixture.file
            TestCase.assertEquals("<html></html>", injectedFile.text)
            myFixture.checkResult(
                "Foo.kt", mkFoo(
                    """   <html></html>""", "HTML"
                ), true
            )
        }
    }
    
    @OptIn(KtAllowAnalysisOnEdt::class)
    fun `test pressing tab in fragment-editor with multiline-text`() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(
                    """
                    {
                      "abc": 1<caret>
                    }
                """,
                    "JSON"
                )

            )

            // user opens the fragment editor
            val quickEditHandler = QuickEditAction().invokeImpl(project, myInjectionFixture.topLevelEditor, myInjectionFixture.topLevelFile)

            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor(quickEditHandler)
            TestCase.assertEquals("indent should be trimmed from the injected text", "{\n  \"abc\": 1\n}", fragmentEditorFixture.file.text)
            TestCase.assertEquals(quickEditHandler.newFile, fragmentEditorFixture.file)

            // user selects all text in the fragment editor and presses <tab> button
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_SELECT_ALL)
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_EDITOR_INDENT_SELECTION)
            PsiDocumentManager.getInstance(project).commitAllDocuments()

            // check that everything is fine
            TestCase.assertTrue(quickEditHandler.isValid)
            TestCase.assertEquals(
                "text should appear shifted in the fragment editor",
                "  {\n    \"abc\": 1\n  }",
                quickEditHandler.newFile.text
            )
            myInjectionFixture.assertInjectedContent(
                "injected text should appear shifted in the original file," +
                        " no trim indent should be applied as long as fragment editor is still open",
                listOf("  {\n    \"abc\": 1\n  }")
            )
            TestCase.assertEquals("active file should not be equal", quickEditHandler.newFile, fragmentEditorFixture.file)

            myFixture.checkResult(
                "Foo.kt", mkFoo(
                    """
                      {
                        "abc": 1
                      }
                """,
                    "JSON"
                ), true
            )

            // now user closes the fragment editor
            quickEditHandler.closeEditorForTest()
            PsiDocumentManager.getInstance(project).reparseFiles(listOf(myInjectionFixture.topLevelFile.virtualFile), true)

            myInjectionFixture.assertInjectedContent(
                "injected text should not appear shifted anymore in the original file," +
                        " the trim indent should be applied as long as fragment editor is closed",
                listOf("{\n  \"abc\": 1\n}")
            )
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testNewLineJSONAuthoComma() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(
                    """
                    {
                      "abc": 1<caret>
                    }
                """,
                    "JSON"
                )
            )
            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
            myFixture.checkResult(
                mkFoo(
                    """
                    {
                      "abc": 1,
                      
                    }
                """,
                    "JSON"
                )
            )
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testJsonWrapBrackets() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(
                    """
                    {
                        "abc": 1,
                        "fffdd": <caret>{
                            "select abc where ": [1, 2, 3]
                        }
                    }
                """,
                    "JSON"
                )
            )
            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()

            val selectionRange = "{\n        \"select abc where \": [1, 2, 3]\n    }".let { toSelect ->
                TextRange.from(fragmentEditorFixture.file.text.let { text ->
                    text.indexOf(toSelect).also { if (it == -1) throw IllegalArgumentException("can't find text: '$toSelect' in '$text'") }
                }, toSelect.length)
            }

            fragmentEditorFixture.editor.selectionModel.setSelection(selectionRange.startOffset, selectionRange.endOffset)
            fragmentEditorFixture.type("boo")
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
            myFixture.checkResult(
                mkFoo(
                    """
                    {
                        "abc": 1,
                        "fffdd": boo
                      
                    }
                """,
                    "JSON"
                )
            )
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testJsonDeleteAll() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(
                    """
                    {
                        "abc": 1,
                        "fffdd": <caret>{
                            "select abc where ": [1, 2, 3]
                        }
                    }
                """,
                    "JSON"
                )
            )
            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_SELECT_ALL)
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)

            myFixture.checkResult(
                mkFoo(
                    """""",
                    "JSON"
                )
            )
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testJsonDeleteAllAndEmptyLines() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(
                    """
                    {
                        "abc": 1,
                        "fffdd": <caret>{
                            "select abc where ": [1, 2, 3]
                        }
                    }
                    
                """,
                    "JSON"
                )
            )
            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_SELECT_ALL)
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)

            myFixture.checkResult(
                mkFoo(
                    """""",
                    "JSON"
                )
            )
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testJsonDuplicateAll() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(
                    """
                    {
                        "abc": 1,
                        "fffdd": <caret>{
                            "select abc where ": [1, 2, 3]
                        }
                    }

                """,
                    "JSON"
                )
            )
            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_SELECT_ALL)
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE)

            myFixture.checkResult(
                mkFoo(
                    """
                    {
                        "abc": 1,
                        "fffdd": {
                            "select abc where ": [1, 2, 3]
                        }
                    }
                    {
                        "abc": 1,
                        "fffdd": {
                            "select abc where ": [1, 2, 3]
                        }
                    }

                """,
                    "JSON"
                )
            )
        }
    }


    @OptIn(KtAllowAnalysisOnEdt::class)
    fun testJsonReplaceAll() {
        allowAnalysisOnEdt {
            myFixture.configureByText(
                "Foo.kt", mkFoo(
                    """
                    {<caret>
                        "select abc where ": [1, 2, 3]
                    }
                """,
                    "JSON"
                )
            )
            val fragmentEditorFixture = myInjectionFixture.openInFragmentEditor()
            fragmentEditorFixture.performEditorAction(IdeActions.ACTION_SELECT_ALL)
            WriteCommandAction.runWriteCommandAction(project) {
                fragmentEditorFixture.editor.document.setText("{\"abc\": { \n \"def\": 1 \n}")
            }

            myFixture.checkResult(
                mkFoo(
                    """
                    {"abc": { 
                     "def": 1 
                    }
                """,
                    "JSON"
                )
            )
        }
    }

}