// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.injected.editor.EditorWindow
import com.intellij.json.codeinsight.JsonStandardComplianceInspection
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.impl.TrailingSpacesStripper
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.injection.Injectable
import com.intellij.psi.util.parents
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.testFramework.fixtures.injectionForHost
import com.intellij.util.castSafelyTo
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase.registerJsonSchema
import junit.framework.TestCase
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.intellij.plugins.intelliLang.inject.UnInjectLanguageAction
import org.jetbrains.concurrency.collectResults
import org.jetbrains.concurrency.runAsync
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl
import java.util.function.Predicate

class YamlMultilineInjectionTest : BasePlatformTestCase() {

  private val myInjectionFixture: InjectionTestFixture
    get() = InjectionTestFixture(myFixture)

  override fun setUp() {
    super.setUp()
    registerJsonSchema(myFixture, """
      {
        "properties": {
          "X": {
            "x-intellij-language-injection": "XML"
          },
          "myyaml": {
            "x-intellij-language-injection": "yaml"
          }
        }
      }
    """.trimIndent(), ".json", Predicate { true })

  }

  fun testSeparateLinesQuotesXmlInjection() {
    myFixture.configureByText("test.yaml", """
        X: "<html><caret>
             <body>boo</body>
           </html>"
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertInjectedAndLiteralValue("<html>\n<body>boo</body>\n</html>")
  }
  
  fun testBashCommentInjection() {
    myFixture.configureByText("test.yaml", """
      # language=bash
      commands:
        - sudo rm -rf /
        - df -h
    """.trimIndent())

    myInjectionFixture.assertInjected(
      injectionForHost("sudo rm -rf /").hasLanguage("Shell Script"),
      injectionForHost("df -h").hasLanguage("Shell Script"),
    )
  }


  fun testFoldedXmlInjection() {
    myFixture.configureByText("test.yaml", """
        X: >
          <html><caret>
               <body>boo</body>
          </html>
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertInjectedAndLiteralValue("""
      |<html>
      |     <body>boo</body>
      |</html>""".trimMargin())
  }

  fun testBlockInjection() {
    myFixture.configureByText("test.yaml", """
        X: |
          <html><caret>
               <body>boo</body>
          </html>
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertInjectedAndLiteralValue("""
      |<html>
      |     <body>boo</body>
      |</html>""".trimMargin())

  }

  fun testEmptyLineDeleted() {
    myFixture.configureByText("test.yaml", """
      long:
        long:
          nest:
            #language=XML
            abc: |
              <html>
              <caret>
              </html>
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    PsiDocumentManager.getInstance(project).commitDocument(myFixture.getDocument(myFixture.file))
    myInjectionFixture.assertInjectedContent("<html>\n\n</html>")
    myFixture.checkResult("""
      long:
        long:
          nest:
            #language=XML
            abc: |
              <html>
            
              </html>
    """.trimIndent())
  }


  fun testYamlToYamlInjection() {
    myFixture.configureByText("test.yaml", """
        myyaml: |
          ro<caret>ot:
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("yaml")
    assertInjectedAndLiteralValue("root:")
    myFixture.openFileInEditor(myInjectionFixture.topLevelFile.virtualFile)
    myFixture.editor.caretModel.run { moveToOffset(offset + 3) }
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      |myyaml: |
      |  root:
      |    """.trimMargin())
  }

  fun testNewLineInInjectedXML() {
    myFixture.configureByText("test.yaml", """
      long:
        long:
          nest:
            #language=XML
            abc: |
              <xml><caret></xml>
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      long:
        long:
          nest:
            #language=XML
            abc: |
              <xml>
                  <caret>
              </xml>
    """.trimIndent())
  }
  
  fun testNewLineInInjectedYamlCaretMoved() {
    myFixture.configureByText("test.yaml", """
      |myyaml: |
      |  boo:
      |    - 1
      |<caret>
      |    - 2
      |      
      |  """.trimMargin())

    myInjectionFixture.assertInjectedLangAtCaret("yaml")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      |myyaml: |
      |  boo:
      |    - 1
      |
      |<caret>
      |    - 2
      |      
      |  """.trimMargin())
    myInjectionFixture.assertInjectedContent("boo:\n  - 1\n\n\n  - 2\n    \n")
  }
  
  fun testNewLineInInjectedXMLinNested() {
    myFixture.configureByText("test.yaml", """
      long:
        long:
          nest:
            #language=XML
            abc: |
                <xml1>
                    <xml2>
                        <xml3><caret></xml3>
                    </xml2>
                </xml1>
        
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      long:
        long:
          nest:
            #language=XML
            abc: |
                <xml1>
                    <xml2>
                        <xml3>
                            <caret>
                        </xml3>
                    </xml2>
                </xml1>
                
    """.trimIndent())
  }

  fun testTemporaryInjection() {
    myFixture.configureByText("test.yaml", """
      long:
        long:
          nest:
            abc: |
                <xml1>
                    <xml2>
                        <xml3><caret></xml3>
                    </xml2>
                </xml1>
        
    """.trimIndent())

    assertNotNull(myFixture.getAvailableIntention("Inject language or reference"))
    InjectLanguageAction.invokeImpl(project, myFixture.editor, myFixture.file, Injectable.fromLanguage(Language.findLanguageByID("XML")))
    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertNotNull(myFixture.getAvailableIntention("Uninject language or reference"))
    UnInjectLanguageAction.invokeImpl(project, myFixture.editor, myFixture.file)
    myInjectionFixture.assertInjectedLangAtCaret(null)
  }

  fun testNewLineInPlainTextInjectedXMLinNested() {
    myFixture.configureByText("test.yaml", """
      long:
        long:
          nest:
            #language=XML
            abc:
              <xml1>
                  <xml2>
                      <xml3><caret></xml3>
                  </xml2>
              </xml1>
        
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      long:
        long:
          nest:
            #language=XML
            abc:
              <xml1>
                  <xml2>
                      <xml3>
                          
                      </xml3>
                  </xml2>
              </xml1>
        
    """.trimIndent())
  }

  fun testNewLineInInjectedXMLinNestedAndUndoInMultithreading() {
    // reuse testNewLineInInjectedXMLinNested fixture
    testNewLineInInjectedXMLinNested()

    // then undo and try again to check that all editors are disposed properly
    val hostEditor = myFixture.editor.let { it.castSafelyTo<EditorWindow>()?.delegate ?: it }
    val hostFile = PsiDocumentManager.getInstance(project).getPsiFile(hostEditor.document) ?: throw AssertionError("no psi file")
    val hostVFile = hostFile.virtualFile
    myFixture.openFileInEditor(hostVFile)
    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val simultaneouslyRequestedEditorsByOtherThreads = (1..10).map {
      runAsync {
        runReadAction {
            val hostCaret = hostEditor.caretModel.offset
            val psiElement = InjectedLanguageManager.getInstance(project).findInjectedElementAt(hostFile, hostCaret)
                             ?: throw AssertionError("can't get injected element in the background at $hostCaret")
            InjectedLanguageUtil.getInjectedEditorForInjectedFile(hostEditor, hostEditor.caretModel.currentCaret,
                                                                  psiElement.containingFile)
        }
      }
    }
    PlatformTestUtil.waitForPromise(simultaneouslyRequestedEditorsByOtherThreads.collectResults(), 5000)
    myFixture.performEditorAction(IdeActions.ACTION_UNDO)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      long:
        long:
          nest:
            #language=XML
            abc: |
                <xml1>
                    <xml2>
                        <xml3>
                            <caret>
                        </xml3>
                    </xml2>
                </xml1>
                
    """.trimIndent())
  }

  
  fun testInjectedJsonBlock() {
    myFixture.configureByText("test.yaml", """
    myyaml:
      #language=JSON
      json: |
        {
          "inner" : {<caret>}
        }

    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("JSON")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      myyaml:
        #language=JSON
        json: |
          {
            "inner" : {
              
            }
          }
          
    """.trimIndent())
  }
  
  fun testInjectedJsonBlockQuickfix() {
    myFixture.enableInspections(JsonStandardComplianceInspection::class.java)
    myFixture.configureByText("test.yaml", """
    myyaml:
      #language=JSON
      json: |
        ab<caret>c: 1

    """.trimIndent())
    
    myFixture.doHighlighting()
    myInjectionFixture.assertInjectedLangAtCaret("JSON")
    val wrapQuickfix = myFixture.getAvailableIntention("Wrap with double quotes")!!
    myFixture.launchAction(wrapQuickfix)
    myFixture.checkResult("""
    myyaml:
      #language=JSON
      json: |
        "abc": 1
  
    """.trimIndent())
  }
  
  fun testInjectedFlatIndentBlock() {
    myFixture.configureByText("test.yaml", """
      myyaml:
        #language=Java
        after: |
          class B {
              <caret>
          }

    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("JAVA")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      myyaml:
        #language=Java
        after: |
          class B {
              
              
          }
            
    """.trimIndent())
  }
  
  fun testInjectedStartIndented() {
    myFixture.configureByText("test.yaml", """
    myyaml:
      #language=JSON
      json: |<caret>

    """.trimIndent())

    myInjectionFixture.assertInjectedContent("")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      myyaml:
        #language=JSON
        json: |
          <caret>

    """.trimIndent())
  }


  fun testInjectedJsonComma() {
    myFixture.configureByText("test.yaml", """
      long:
        long:
          nest:
            #language=JSON
            abc: |
              {
                "jkey": 1<caret>
              }
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("JSON")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      long:
        long:
          nest:
            #language=JSON
            abc: |
              {
                "jkey": 1,
                
              }
    """.trimIndent())
    myInjectionFixture.assertInjectedContent("{\n  \"jkey\": 1,\n  \n}")
  }


  fun testYamlToYamlInjection2() {
    myFixture.configureByText("test.yaml", """
        myyaml: |
          root:<caret>
          
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("yaml")
    assertEquals("literal text should be", "root:\n", literalTextAtTheCaret)
    myInjectionFixture.assertInjectedContent("root:\n")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      |myyaml: |
      |  root:
      |    
      |  """.trimMargin())
    myInjectionFixture.assertInjectedContent("root:\n  \n")
    PsiDocumentManager.getInstance(project).commitDocument(myFixture.getDocument(myFixture.file))
    myFixture.type("abc:")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      |myyaml: |
      |  root:
      |    abc:
      |      
      |  """.trimMargin())
    myInjectionFixture.assertInjectedContent("root:\n  abc:\n    \n")
    myFixture.type("def: 1")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      |myyaml: |
      |  root:
      |    abc:
      |      def: 1
      |      
      |  """.trimMargin())
    myInjectionFixture.assertInjectedContent("root:\n  abc:\n    def: 1\n    \n")
  }
  
  fun testYamlToYamlEnterInBefore() {
    myFixture.configureByText("test.yaml", """
      |myyaml: |
      |  root:
      |    abc:<caret>
      |      def: 1
      |      
      |  """.trimMargin())

    myInjectionFixture.assertInjectedLangAtCaret("yaml")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      |myyaml: |
      |  root:
      |    abc:
      |      
      |      def: 1
      |      
      |  """.trimMargin())
    myInjectionFixture.assertInjectedContent("root:\n  abc:\n    \n    def: 1\n    \n")
  }
  
  fun testYamlToYamlEnterInBeforeNoInject() {
    myFixture.configureByText("test.yaml", """
      |myyaml:  
      |  root:
      |    abc:<caret>
      |      def: 1
      |      
      |  """.trimMargin())
    
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      |myyaml:  
      |  root:
      |    abc:
      |      
      |      def: 1
      |      
      |  """.trimMargin())
  }
  
  fun testYamlToYamlReformat() {
    myFixture.configureByText("test.yaml", """
      |myyaml: |
      |  root:
      |    abc:
      |      def: 1<caret>
      |      
      |  """.trimMargin())
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
    myFixture.checkResult("""
      |myyaml: |
      |  root:
      |    abc:
      |      def: 1
      |      
      |  """.trimMargin())
  }
  
  fun testMultilanguageReformat() {
    myFixture.configureByText("test.yaml", """
      myyaml:
        #language=XML
        xml: |
              <xml>
                        <tag>   </tag>
              </xml>
        #language=YAML
        yaml: |
          boo: 
            baz:
              bix: 1
        #language=Java
        after: |
          class B {
              void foo(){}
          } 
        #language=Properties
        prop: |
          prop.a = 1
            prop.b = 4
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
    myFixture.checkResult("""
      |myyaml:
      |  #language=XML
      |  xml: |
      |    <xml>
      |        <tag></tag>
      |    </xml>
      |  #language=YAML
      |  yaml: |
      |    boo:
      |      baz:
      |        bix: 1
      |  #language=Java
      |  after: |
      |    class B {
      |        void foo() {
      |        }
      |    }
      |  #language=Properties
      |  prop: |
      |    prop.a=1
      |    prop.b=4
""".trimMargin())
  }
  
  fun testXmlEmptyLineReformat() {
    myFixture.configureByText("test.yaml", """
      myyaml:
        #language=XML
        xml: |
              <xml>
                  <tag>
                  
                  </tag>
              </xml>
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
    TrailingSpacesStripper.strip(PsiDocumentManager.getInstance(project).getDocument(myFixture.file)!!, false, false)
    myFixture.checkResult("""
      myyaml:
        #language=XML
        xml: |
          <xml>
              <tag>
              
              </tag>
          </xml>
    """.trimIndent())
  }

  fun testBlockInjectionKeep() {
    myFixture.configureByText("test.yaml", """
        X: |+
          <html>
          <body>hello world</body>
          </html<caret>>
          
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertInjectedAndLiteralValue("""
      |<html>
      |<body>hello world</body>
      |</html>
      |""".trimMargin())
  }
  
  fun testBlockInjectionStrip() {
    myFixture.configureByText("test.yaml", """
        X: |-
          <html>
          <body>hello world</body>
          </html<caret>>
          
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val fe = myInjectionFixture.openInFragmentEditor()
    TestCase.assertEquals("""
      |<html>
      |<body>hello world</body>
      |</html>
      |""".trimMargin(), fe.file.text)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", """
      |<html>
      |<body>hello world</body>
      |</html>
      |
      |""".trimMargin(), fe.file.text)
    myFixture.checkResult("""
        X: |-
          <html>
          <body>hello world</body>
          </html>
          
          
    """.trimIndent())
    fe.performEditorAction(IdeActions.ACTION_SELECT_ALL)
    fe.performEditorAction(IdeActions.ACTION_DELETE)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", "", fe.file.text)
    myFixture.checkResult("""
        |X: |-
        |  """.trimMargin())
    
  }
  
  
  fun testPutEnterInTheEnd() {
    myFixture.configureByText("test.yaml", """
        X: |
          <html>
          <body>hello world</body>
          </html<caret>>
          
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val quickEditHandler = QuickEditAction().invokeImpl(project,
                                                        myInjectionFixture.topLevelEditor, myInjectionFixture.topLevelFile)
    val fe = myInjectionFixture.openInFragmentEditor(quickEditHandler)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    TestCase.assertTrue("editor should survive adding enter to the end", quickEditHandler.isValid)
    assertEquals("fragment editor should be", """
      |<html>
      |<body>hello world</body>
      |</html>
      |
      |""".trimMargin(), fe.file.text)
    myFixture.checkResult("""
        X: |
          <html>
          <body>hello world</body>
          </html>
          
          
    """.trimIndent())

    fe.type("footer")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", """
      |<html>
      |<body>hello world</body>
      |</html>
      |footer
      |""".trimMargin(), fe.file.text)
    myFixture.checkResult("""
        X: |
          <html>
          <body>hello world</body>
          </html>
          footer
          
    """.trimIndent())
  }
  
  fun testPutEnterInTheEndWithBlankLine() {
    myFixture.configureByText("test.yaml", """
        X: |
          <html>
          <body>hello world</body>
          </html<caret>>
          
        Y: 12
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val quickEditHandler = QuickEditAction().invokeImpl(project,
                                                        myInjectionFixture.topLevelEditor, myInjectionFixture.topLevelFile)
    val fe = myInjectionFixture.openInFragmentEditor(quickEditHandler)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    TestCase.assertTrue("editor should survive adding enter to the end", quickEditHandler.isValid)
    assertEquals("fragment editor should be", """
      |<html>
      |<body>hello world</body>
      |</html>
      |
      |""".trimMargin(), fe.file.text)
    myFixture.checkResult("""
        X: |
          <html>
          <body>hello world</body>
          </html>
          
          
        Y: 12
    """.trimIndent())

    fe.type("footer")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", """
      |<html>
      |<body>hello world</body>
      |</html>
      |
      |footer""".trimMargin(), fe.file.text)
    myFixture.checkResult("""
        X: |
          <html>
          <body>hello world</body>
          </html>
          
          footer
        Y: 12
    """.trimIndent())
  }
  
  fun testTypeHtmlFromEmpty() {
    myFixture.configureByText("test.yaml", """
        X: |
          <caret>
          
        Y: 12
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val quickEditHandler = QuickEditAction().invokeImpl(project,
                                                        myInjectionFixture.topLevelEditor, myInjectionFixture.topLevelFile)
    val fe = myInjectionFixture.openInFragmentEditor(quickEditHandler)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
    fe.type("<html>")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", "<html></html>\n", fe.file.text)
    myFixture.checkResult("""
        X: |
          <html></html>
          
        Y: 12
    """.trimIndent())
  }
  
  fun testFEinPlainText() {
    myFixture.configureByText("test.yaml", """
        myyaml:
          #language=XML
          xml:
            <xml>
                <caret><aaa></aaa>
            </xml>
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val fe = myInjectionFixture.openInFragmentEditor()
    fe.type(" ")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    myFixture.checkResult("""
      myyaml:
        #language=XML
        xml:
          <xml>
               <aaa></aaa>
          </xml>
    """.trimIndent())  
    fe.type(" ")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    myFixture.checkResult("""
      myyaml:
        #language=XML
        xml:
          <xml>
                <aaa></aaa>
          </xml>
    """.trimIndent())
  }

  fun testSplitHtmlFromEmpty() {
    myFixture.configureByText("test.yaml", """
        X: |
          <html><caret></html>
          
        Y: 12
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val quickEditHandler = QuickEditAction().invokeImpl(project,
                                                        myInjectionFixture.topLevelEditor, myInjectionFixture.topLevelFile)
    val fe = myInjectionFixture.openInFragmentEditor(quickEditHandler)
    fe.editor.caretModel.moveToOffset("<html>".length)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", "<html>\n    \n</html>\n", fe.file.text)
    myFixture.checkResult("""
        X: |
          <html>
              
          </html>
          
        Y: 12
    """.trimIndent())
  }

  fun testIndentInFE() {
    myFixture.configureByText("test.yaml", """
        X: |
          <html>
            <body>hello world</body>
          </html<caret>>
          
        Y: 12
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val quickEditHandler = QuickEditAction().invokeImpl(project,
                                                        myInjectionFixture.topLevelEditor, myInjectionFixture.topLevelFile)
    val fe = myInjectionFixture.openInFragmentEditor(quickEditHandler)
    fe.performEditorAction(IdeActions.ACTION_SELECT_ALL)
    fe.performEditorAction(IdeActions.ACTION_EDITOR_INDENT_SELECTION)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    TestCase.assertTrue("editor should survive adding enter to the end", quickEditHandler.isValid)
    assertEquals("fragment editor should be", """
      |    <html>
      |      <body>hello world</body>
      |    </html>
      |
    """.trimMargin(), fe.file.text)
    myFixture.checkResult("""
        X: |
              <html>
                <body>hello world</body>
              </html>
          
        Y: 12
    """.trimIndent())
  }

  fun testYamlToYamlQuotedInFragment() {
    myFixture.configureByText("test.yaml", """
        myyaml: "root: <caret>"
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("yaml")
    assertInjectedAndLiteralValue("root: ")
    val fe = myInjectionFixture.openInFragmentEditor()
    fe.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    myFixture.checkResult("""
      myyaml: "root:\n\
              \  "
    """.trimIndent())
  }

  fun testMultilineInQuotedFragment() {
    myFixture.configureByText("test.yaml", """
        X: "<caret>"
        
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    val fe = myInjectionFixture.openInFragmentEditor()
    fe.type("""
      <html>
      <body>
      <h1>
    """.trimIndent())
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertEquals("fragment editor should be", """
      <html>
          <body>
              <h1></h1>
          </body>
      </html>
    """.trimIndent(), fe.file.text)
    myFixture.checkResult("""
      X: "<html>\n\
         \    <body>\n\
         \        <h1></h1>\n\
         \    </body>
         </html>"
      
    """.trimIndent())
  }


  fun testYamlToYamlQuotedMultiline() {
    myFixture.configureByText("test.yaml", """
        myyaml: "abc: \<caret>
                "
    """.trimIndent())

    myInjectionFixture.assertInjectedContent("abc: \\\n")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      myyaml: "abc: \

              "
    """.trimIndent())
    myInjectionFixture.assertInjectedContent("abc: \\\n\n")
  }
  
  fun testYamlToYamlQuotedMultilineDoubleEscape() {
    myFixture.configureByText("test.yaml", """
      myyaml: "abc: \
                    \<caret>
      "
    """.trimIndent())

    myInjectionFixture.assertInjectedContent("abc: \\\n\\\n")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    myFixture.checkResult("""
      myyaml: "abc: \
                    \
      
      "
    """.trimIndent())
    myInjectionFixture.assertInjectedContent("abc: \\\n\\\n\n")
  }
  

  private fun assertInjectedAndLiteralValue(expectedText: String) {
    assertEquals("fragment editor should be", expectedText, myInjectionFixture.openInFragmentEditor().file.text)
    assertEquals("literal text should be", expectedText, literalTextAtTheCaret)
  }

  val literalTextAtTheCaret: String
    get() {
      val elementAt = myInjectionFixture.topLevelFile.findElementAt(myInjectionFixture.topLevelCaretPosition)
      return elementAt?.parents(true)?.mapNotNull { it.castSafelyTo<YAMLScalarImpl>() }?.firstOrNull()
               ?.let { psi -> psi.contentRanges.joinToString("") { it.subSequence(psi.text) } }
             ?: throw AssertionError("no literal element at the caret position, only $elementAt were found")
    }

}