// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.suggested

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution
import com.intellij.refactoring.suggested._suggestedChangeSignatureNewParameterValuesForTests
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator

class PySuggestedRefactoringTest : PyTestCase() {

  override fun setUp() {
    super.setUp()
    _suggestedChangeSignatureNewParameterValuesForTests = {
      SuggestedRefactoringExecution.NewParameterValue.Expression(
        PyElementGenerator.getInstance(myFixture.project).createExpressionFromText(LanguageLevel.getLatest(), "$it")
      )
    }
  }

  // PY-42285
  fun testImportedClassRename() {
    (myFixture as? CodeInsightTestFixtureImpl)?.canChangeDocumentDuringHighlighting(true)
    doRenameImportedTest(
      """
        class A<caret>C:
          pass
      """.trimIndent(),
      """
        class ABC:
          pass
      """.trimIndent(),
      "AC",
      "ABC",
      "B"
    )
  }

  // PY-42285
  fun testImportedFunctionRename() {
    doRenameImportedTest(
      """
        def a<caret>c():
          pass
      """.trimIndent(),
      """
        def abc():
          pass
      """.trimIndent(),
      "ac",
      "abc",
      "b",
      changeSignatureIntention()
    )
  }

  // PY-42285
  fun testMethodRename() {
    doRenameTest(
      """
        class A:
          def method<caret>(self):
            pass
            
        a = A()
        a.method()
      """.trimIndent(),
      """
        class A:
          def method1(self):
            pass
            
        a = A()
        a.method1()
      """.trimIndent(),
      "method",
      "method1",
      "1",
      changeSignatureIntention()
    )
  }

  // PY-49466
  fun testMethodRenameToStartingWithKeywordName() {
    doNoIntentionTest(
      """
        def test<caret>(): pass
      """.trimIndent(),
      intention = changeSignatureIntention()
    ) {
      repeat("test".length) { performAction(IdeActions.ACTION_EDITOR_BACKSPACE) }
      type("def")
    }
  }

  // PY-42285
  fun testImportedVariableRename() {
    doRenameImportedTest(
      """
        A<caret>C = 10
      """.trimIndent(),
      """
        ABC = 10
      """.trimIndent(),
      "AC",
      "ABC",
      "B"
    )
  }

  // PY-42285
  fun testInstanceVariableRename() {
    doRenameTest(
      """
        class A:
          def __init__(self):
            self.a<caret>c = 10
            
        a = A()
        a.ac
      """.trimIndent(),
      """
        class A:
          def __init__(self):
            self.abc = 10
            
        a = A()
        a.abc
      """.trimIndent(),
      "ac",
      "abc",
      "b"
    )
  }

  // PY-42285
  fun testClassVariableRename() {
    doRenameTest(
      """
        class A:
            a<caret>c = 10

        A.ac
      """.trimIndent(),
      """
        class A:
            abc = 10

        A.abc
      """.trimIndent(),
      "ac",
      "abc",
      "b"
    )
  }

  // PY-42285
  fun testParameterRename() {
    doRenameTest(
      """
        def foo(pa<caret>am):
          print(paam)
      """.trimIndent(),
      """
        def foo(param):
          print(param)
      """.trimIndent(),
      "paam",
      "param",
      "r",
      changeSignatureIntention()
    )
  }

  // PY-42285
  fun testPropertySetterParameterRename() {
    doRenameTest(
      """
        class A:
            def __init__(self):
                self._a = 0

            @property
            def a(self):
                return self._a

            @a.setter
            def a(self, val<caret>):
                self._a = val


        a = A()
        print(a.a)
        a.a = 10
        print(a.a)
      """.trimIndent(),
      """
        class A:
            def __init__(self):
                self._a = 0

            @property
            def a(self):
                return self._a

            @a.setter
            def a(self, value):
                self._a = value


        a = A()
        print(a.a)
        a.a = 10
        print(a.a)
      """.trimIndent(),
      "val",
      "value",
      "ue"
    )
  }

  // PY-42285
  fun testPropertySetterParameterRetype() {
    // rename has no ability to track disappeared parameter

    doNoIntentionTest(
      """
        class A:
            def __init__(self):
                self._a = 0

            @property
            def a(self):
                return self._a

            @a.setter
            def a(self, bbb<caret>):
                self._a = bbb


        a = A()
        print(a.a)
        a.a = 10
        print(a.a)
      """.trimIndent(),
      intention = RefactoringBundle.message("suggested.refactoring.rename.intention.text", "bbb", "zzz")
    ) {
      repeat(3) { performAction(IdeActions.ACTION_EDITOR_BACKSPACE) }
      type("zzz")
    }
  }

  // PY-42285
  fun testReorderParametersWithDefaultValues() {
    doChangeSignatureTest(
      """
        def foo(<caret>a="a", b="b"): pass
        
        
        foo("a", "b")
        foo("a")
      """.trimIndent(),
      """
        def foo(b="b", a="a"): pass
        
        
        foo("b", "a")
        foo(a="a")
      """.trimIndent(),
    ) {
      replaceTextAtCaret("a=\"a\", b=\"b\"", "b=\"b\", a=\"a\"")
    }
  }

  // PY-42285
  fun testAddPositionalOnlyMarkerAtTheEnd() {
    doChangeSignatureTest(
      """
        def foo(p1=None, p2=None<caret>):
            print(p1, p2)


        foo(p2=2)
      """.trimIndent(),
      """
        def foo(p1=None, p2=None, /):
            print(p1, p2)


        foo(None, 2)
      """.trimIndent(),

    ) {
      type(", /")
    }
  }

  // PY-42285
  fun testAddPositionalOnlyParameterWithDefaultValueAtTheBeginning() {
    _suggestedChangeSignatureNewParameterValuesForTests = { SuggestedRefactoringExecution.NewParameterValue.None }

    doChangeSignatureTest(
      """
        def foo(<caret>p2=None, /):
            print(p2) 


        foo(2)
      """.trimIndent(),
      """
        def foo(p1=None, p2=None, /):
            print(p2)
        
        
        foo(None, 2)
      """.trimIndent(),
    ) {
      type("p1=None, ")
    }
  }

  // PY-42285
  fun testAddKeywordOnlyMarkerInTheMiddle() {
    doChangeSignatureTest(
      """
        def foo(a, <caret>b):
          pass

        def bar():
          foo(1, 2)
          foo(3, 4)
      """.trimIndent(),
      """
        def foo(a, *, b):
          pass

        def bar():
          foo(1, b=2)
          foo(3, b=4)
      """.trimIndent(),
    ) {
      type("*, ")
    }
  }

  // PY-42285
  fun testRemoveDefaultValueNothingSpecifiedInstead() {
    _suggestedChangeSignatureNewParameterValuesForTests = { SuggestedRefactoringExecution.NewParameterValue.None }

    doChangeSignatureTest(
      """
        def foo(a<caret>=1): pass
        
        
        foo()
      """.trimIndent(),
      """
        def foo(a): pass
        

        foo(1)
      """.trimIndent(),
    ) {
      replaceTextAtCaret("=1", "")
    }
  }

  // PY-42285
  fun testRemoveDefaultValue() {
    doChangeSignatureTest(
      """
        def foo(a<caret>=1): pass
        
        
        foo()
      """.trimIndent(),
      """
        def foo(a): pass
        

        foo(0)
      """.trimIndent(),
    ) {
      replaceTextAtCaret("=1", "")
    }
  }

  // PY-42285
  fun testAddParameterBeforeKeywordContainer() {
    doChangeSignatureTest(
      """
        def foo(<caret>**kwargs):
            for key, value in kwargs.items():
                print("%s = %s" % (key, value))


        foo(name="geeks", ID="101", language="Python")
      """.trimIndent(),
      """
        def foo(a, **kwargs):
            for key, value in kwargs.items():
                print("%s = %s" % (key, value))


        foo(0, name="geeks", ID="101", language="Python")
      """.trimIndent(),
    ) {
      type("a, ")
    }
  }

  // PY-42285
  fun testDontMergeWithParameterAfter1() {
    doChangeSignatureTest(
      """
        def foo(<caret>**kwargs): pass
        
        
        foo(b=2, c=3)
      """.trimIndent(),
      """
        def foo(a=0, **kwargs): pass
        
        
        foo(b=2, c=3)
      """.trimIndent(),
    ) {
      type("a=")
      type("0, ")
    }
  }

  // PY-42285
  fun testDontMergeWithParameterAfter2() {
    doChangeSignatureTest(
      """
        def foo(<caret>b=2): pass
        
        
        foo(b=2)
      """.trimIndent(),
      """
        def foo(a=0, b=2): pass
        
        
        foo(b=2)
      """.trimIndent(),
    ) {
      type("a=")
      type("0, ")
    }
  }

  // PY-42285
  fun testSpecifyAnotherDefaultValueInBalloon() {
    doChangeSignatureTest(
      """
        def foo(<caret>): pass
        
        
        foo()
      """.trimIndent(),
      """
        def foo(b=123): pass
        
        
        foo(0)
      """.trimIndent(),
    ) {
      type("b=123")
    }
  }

  // PY-42285
  fun testDontSpecifyDefaultValueInBalloon() {
    _suggestedChangeSignatureNewParameterValuesForTests = { SuggestedRefactoringExecution.NewParameterValue.None }

    doChangeSignatureTest(
      """
        def foo(<caret>): pass
        
        
        foo()
      """.trimIndent(),
      """
        def foo(b=123): pass
        
        
        foo()
      """.trimIndent(),
    ) {
      type("b=123")
    }
  }

  // PY-42285
  fun testPuttingOptionalBetweenOptionalAndSlash() {
    doChangeSignatureTest(
      """
        def f(p2<caret>=None, /): pass
        
        
        f(1)
      """.trimIndent(),
      """
        def f(p1=None, p2=None, /): pass
        
        
        f(1, 0)
      """.trimIndent(),
    ) {
      performAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      type("1")
      myFixture.editor.caretModel.moveCaretRelatively(7, 0, false, false, false)
      type("p2=")
      type("N")
      type("o")
      type("n")
      type("e")
      type(",")
      type(" ")
    }
  }

  // PY-42285
  fun testPuttingNamedBetweenNamed() {
    doChangeSignatureTest(
      """
        def f(p2<caret>, p3): pass
        
        
        f(2, 3)
      """.trimIndent(),
      """
        def f(p1, p2, p3): pass
        
        
        f(2, 0, 3)
      """.trimIndent(),
    ) {
      performAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      type("1")
      myFixture.editor.caretModel.moveCaretRelatively(2, 0, false, false, false)
      type("p")
      type("2")
      type(",")
      type(" ")
    }
  }

  // PY-42285
  fun testPuttingOptionalBetweenNamedAndOptional() {
    doChangeSignatureTest(
      """
        def f(p2<caret>, p3=None): pass
        
        
        f(2, 3)
      """.trimIndent(),
      """
        def f(p1, p2=None, p3=None): pass
        
        
        f(2, 0, 3)
      """.trimIndent(),
    ) {
      performAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      type("1")
      myFixture.editor.caretModel.moveCaretRelatively(2, 0, false, false, false)
      type("p2=")
      type("N")
      type("o")
      type("n")
      type("e")
      type(",")
      type(" ")
    }
  }

  // PY-42285
  fun testRenamingParameterOnSeparateLine() {
    doChangeSignatureTest(
      """
        def f(
          base<caret>
        ): pass
        
        
        f(f("base"))
      """.trimIndent(),
      """
        def f(
          bases
        ): pass
        
        
        f(f("base"))
      """.trimIndent(),

    ) {
      type("s")
    }
  }

  // PY-42285
  fun testNoIntentionOnMakingFunctionAsync() {
    doNoIntentionTest(
      """
        <caret>def foo(a, b): pass
      """.trimIndent(),
      intention = changeSignatureIntention()
    ) {
      type("async ")
    }
  }

  // PY-42285
  fun testNoIntentionOnMakingFunctionSync() {
    doNoIntentionTest(
      """
        async <caret>def foo(a, b): pass
      """.trimIndent(),
      intention = changeSignatureIntention()
    ) {
      repeat("async ".length) { performAction(IdeActions.ACTION_EDITOR_BACKSPACE)}
    }
  }

  // PY-42285
  fun testRetypedFunctionName() {
    doChangeSignatureTest(
      """
        def foo<caret>(a, b): pass
        
        
        foo(1, 2)
      """.trimIndent(),
      """
        def bar(a, b): pass
        
        
        bar(1, 2)
      """.trimIndent(),
    ) {
      repeat(" foo".length) { performAction(IdeActions.ACTION_EDITOR_BACKSPACE) }
      type(" bar")
    }
  }

  // PY-42285
  fun testClassMethodSignature() {
    doChangeSignatureTest(
      """
        class A:
          @classmethod
          def method(cls, a<caret>):
            pass
            

        A.method(5)
      """.trimIndent(),
      """
        class A:
          @classmethod
          def method(cls, a, b):
            pass


        A.method(5, 0)
      """.trimIndent(),
    ) {
      type(", b")
    }
  }

  // PY-42285
  fun testRetypedParameter1() {
    doChangeSignatureTest(
      """
        def __func__0(a<caret>):
            return a.swapcase()
        d = {'1': __func__0(__func__0("abc")), '2': __func__0(__func__0("abc"))}
      """.trimIndent(),
      """
        def __func__0(b):
            return b.swapcase()
        d = {'1': __func__0(__func__0("abc")), '2': __func__0(__func__0("abc"))}
      """.trimIndent(),
    ) {
      performAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      type("b")
    }
  }

  // PY-42285
  fun testRetypedParameter2() {
    doChangeSignatureTest(
      """
        def __func__0(a<caret>):
            return a.swapcase()
        d = {'1': __func__0(__func__0("abc")), '2': __func__0(__func__0("abc"))}
      """.trimIndent(),
      """
        def __func__0(bbb):
            return bbb.swapcase()
        d = {'1': __func__0(__func__0("abc")), '2': __func__0(__func__0("abc"))}
      """.trimIndent(),
    ) {
      performAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      type("bbb")
    }
  }

  // PY-42285
  fun testRetypedParameter3() {
    doChangeSignatureTest(
      """
        def __func__0(aaa<caret>):
            return aaa.swapcase()
        d = {'1': __func__0(__func__0("abc")), '2': __func__0(__func__0("abc"))}
      """.trimIndent(),
      """
        def __func__0(b):
            return b.swapcase()
        d = {'1': __func__0(__func__0("abc")), '2': __func__0(__func__0("abc"))}
      """.trimIndent(),
    ) {
      repeat("aaa".length) { performAction(IdeActions.ACTION_EDITOR_BACKSPACE) }
      type("b")
    }
  }

  // PY-42285
  fun testRenameToUnsupportedIdentifier() {
    doNoIntentionTest(
      """
        class A:
          def print_r<caret>(self): pass
          
          
        A().print_r()
      """.trimIndent(),
      intention = RefactoringBundle.message("suggested.refactoring.rename.intention.text", "print_r", "print")
    ) {
      repeat("_r".length) { performAction(IdeActions.ACTION_EDITOR_BACKSPACE) }
    }
  }

  // PY-42285
  fun testInvalidElementAccessOnNewParameter() {
    doChangeSignatureTest(
      """
        def greeting(<caret>
            number: int,
            /,
            position: float = 21234,
            *,
            name_family: str
        ) -> str:
            return 'Hello ' + name_family + " " + str(number)
        
        
        greeting(12345432123, 1234, name_family="DIZEL")
      """.trimIndent(),
      """
        def greeting(
                minor: int,
            number: int,
            /,
            position: float = 21234,
            *,
            name_family: str
        ) -> str:
            return 'Hello ' + name_family + " " + str(number)
        
        
        greeting(0, 12345432123, 1234, name_family="DIZEL")
      """.trimIndent()
    ) {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
      type("m")
      type("i")
      type("n")
      type("o")
      type("r")
      type(":")
      type(" ")
      type("i")
      type("n")
      type("t")
      type(",")
    }
  }

  // PY-42285
  fun testRenameParameterOfFunctionWithStub() {
    val testDataPathPrefix = "refactoring/suggested/${getTestName(true)}"
    val source = "aaa.pyi"

    myFixture.copyFileToProject("$testDataPathPrefix/$source", source)

    doNoIntentionTest(
      """
        def foo(p1<caret>):
          print(p1)
      """.trimIndent(),
      intention = changeSignatureIntention()
    ) {
      type("2")
    }
  }

  // EA-252027
  fun testOverload() {
    doNoIntentionTest(
      """
        from typing import overload
        
        @overload
        def foo(p<caret>1: int, p2: int) -> int: ...
        
        def foo(p1, p2): ...
      """.trimIndent(),
      intention = changeSignatureIntention()
    ) {
      type("aram")
    }
  }

  private fun doRenameTest(before: String, after: String, oldName: String, newName: String, type: String, intention: String? = null) {
    myFixture.configureByText(PythonFileType.INSTANCE, before)
    myFixture.checkHighlighting()

    type(type)
    PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()

    executeIntention(intention ?: RefactoringBundle.message("suggested.refactoring.rename.intention.text", oldName, newName))

    myFixture.checkResult(after)
    myFixture.checkHighlighting()
  }

  private fun doRenameImportedTest(
    importedBefore: String,
    importedAfter: String,
    oldName: String,
    newName: String,
    type: String,
    intention: String? = null
  ) {
    val testDataPathPrefix = "refactoring/suggested/${getTestName(true)}"
    val source = "a.py"

    myFixture.copyFileToProject("$testDataPathPrefix/$source", source)
    doRenameTest(importedBefore, importedAfter, oldName, newName, type, intention)
    myFixture.checkResultByFile(source, "$testDataPathPrefix/a.after.py", true)
  }

  private fun doChangeSignatureTest(
    before: String,
    after: String,
    editingActions: () -> Unit
  ) {
    myFixture.configureByText(PythonFileType.INSTANCE, before)
    myFixture.checkHighlighting()

    editingActions()
    PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()

    executeIntention(changeSignatureIntention())

    myFixture.checkResult(after)
    myFixture.checkHighlighting()
  }

  private fun doNoIntentionTest(text: String, intention: String, editingActions: () -> Unit) {
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    myFixture.checkHighlighting()
    editingActions()
    PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
    assertNull(myFixture.getAvailableIntention(intention))
  }

  private fun executeIntention(intention: String) {
    val project = myFixture.project
    myFixture.findSingleIntention(intention).also {
      CommandProcessor.getInstance().executeCommand(
        project, { it.invoke(project, myFixture.editor, myFixture.file) }, it.text, null)
    }
  }

  private fun replaceTextAtCaret(oldText: String, newText: String) = editAction {
    val editor = myFixture.editor
    val offset = editor.caretModel.offset
    val actualText = editor.document.getText(TextRange(offset, offset + oldText.length))
    require(actualText == oldText)
    editor.document.replaceString(offset, offset + oldText.length, newText)
  }

  private fun performAction(actionId: String) {
    myFixture.performEditorAction(actionId)
    PsiDocumentManager.getInstance(myFixture.project).commitDocument(myFixture.editor.document)
  }

  private fun changeSignatureIntention(): String {
    return RefactoringBundle.message("suggested.refactoring.change.signature.intention.text",
                                     RefactoringBundle.message("suggested.refactoring.usages"))
  }
  
  private fun type(text: String) {
    myFixture.type(text)
    PsiDocumentManager.getInstance(myFixture.project).commitDocument(myFixture.editor.document)
  }

  private fun editAction(action: () -> Unit) {
    val psiDocumentManager = PsiDocumentManager.getInstance(myFixture.project)
    executeCommand {
      runWriteAction {
        action()
        psiDocumentManager.commitAllDocuments()
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(myFixture.editor.document)
      }
    }
  }
}