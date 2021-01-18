// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.typeignore

import com.intellij.psi.PsiFile
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.*
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.LanguageLevel

class TypeIgnoreInspectionSuppressorTest: PyTestCase() {

  fun testCaseInsensitiveness() {
    doTestByText("""
      def foo(x: str):
          print(x.bar) # TyPe: IGnore
    """)
  }

  fun testWhitespacesInsensitiveness() {
    doTestByText("""
      def foo(x: str):
          print(x.bar) #type:    ignore    
    """)
  }

  fun testPlainTextCommentAfterwards() {
    doTestByText("""
      def foo(x: str):
          print(x.bar) # type: ignore # plain text comment
    """)
  }

  fun testPlainTextAfterwards() {
    doTestByText("""
      def foo(x: str):
          print(x.<warning descr="Unresolved attribute reference 'bar' for class 'str'">bar</warning>) # type: <warning descr="Unresolved reference 'ignore'">ignore</warning><error descr="End of statement expected"> </error><warning descr="Unresolved reference 'plaintextnotcomment'">plaintextnotcomment</warning>
    """)
  }

  fun testSpaceAfterTypePrefix() {
    doTestByText("""
      def foo(x: str):
          print(x.<warning descr="Unresolved attribute reference 'bar' for class 'str'">bar</warning>) # type : ignore
    """)
  }

  fun testTypeIgnoreD() {
    doTestByText("""
      def foo(x: str):
          print(x.<warning descr="Unresolved attribute reference 'bar' for class 'str'">bar</warning>) # type: <warning descr="Unresolved reference 'ignoreD'">ignoreD</warning>
    """)
  }

  fun testWithNoqa() {
    myFixture.enableInspections(SpellCheckingInspection::class.java)
    doTestByText("""
      def foo(x: str):
          print(x.bar + 'ajsd') # type: ignore # noqa
    """)
  }

  fun testTwoCodes() {
    doTestByText("""
      def foo(x: str):
          print(x.bar) #type: ignore[foo , bar-baz]
    """)
  }

  fun testTypeIgnoreCodesIgnoredCurrently() {
    doTestByText("""
      def foo(x: str):
          print(x.<warning descr="Unresolved attribute reference 'bar' for class 'str'">bar</warning>)
          print(x.bar)  # type: ignore
          print(x.bar)  # type: ignore[attr-defined]
          print(x.bar)  # type: ignore[call-arg]
          print(x.bar)  # type: ignore[whatever]
    """)
  }

  fun testIgnoreType() {
    doTestByText("""
      print(2 + 'foo') # type: ignore
      print(2 + <warning descr="Expected type 'int', got 'str' instead">'foo'</warning>)
    """)
  }

  fun testIgnoreUnresolvedReferenceAttribute() {
    doTestByText("""
      def foo(x: str):
          print(x.bar) # type: ignore
          print(x.<warning descr="Unresolved attribute reference 'bar' for class 'str'">bar</warning>)
    """)
  }

  fun testIgnoreUnresolvedReferenceImport() {
    doTestByText("""
      import frobnicate  # type: ignore
      <warning descr="Unused import statement 'import frobnicate1'">import <error descr="No module named 'frobnicate1'">frobnicate1</error></warning>
    """)
  }

  fun testTypeHint() {
    doTestByText("""
      class A:
        pass
    
      A.foo = 10 # type: ignore[attr-defined]
    """)
  }

  fun testIgnoreUnexpectedArgument() {
    doTestByText("""
        def foo(s: str) -> None:
            print(s)

        foo('foo', 'bar')  # type: ignore
        foo('foo', <warning descr="Unexpected argument">'bar'</warning>)
    """)
  }

  fun testIgnoreUnexpectedReturnType() {
    doTestByText("""
      def func(x: int) -> str:
          return x + 1  # type: ignore
          
      def func1(x: int) -> str:
          return <warning descr="Expected type 'str', got 'int' instead">x + 1</warning>
    """)
  }

  fun testIgnoreRedeclaredWithoutUsage() {
    doTestByText("""
      class A:
          def __init__(self, x: int) -> None: ...

      class A:  # type: ignore
          def __init__(self, x: str) -> None: ...

      class <warning descr="Redeclared 'A' defined above without usage">A</warning>:
          def __init__(self, x: str) -> None: ...
    """)
  }

  fun testIgnoreFinal() {
    doTestByText("""
      from typing_extensions import final
      @final
      class A:
          pass

      class B(A): # type: ignore
          pass

      class <warning descr="'A' is marked as '@final' and should not be subclassed">C</warning>(A):
          pass
    """)
  }

  fun testIgnoreProtocol() {
    doTestByText("""
      from typing import NewType, Protocol

      class Id1(Protocol):
          code: int
      
      UserId1 = NewType('UserId1', Id1) # type: ignore
      UserId2 = NewType('UserId2', <warning descr="NewType cannot be used with protocol classes">Id1</warning>)
      """)
  }

  fun testIgnoreTypedDict() {
    doTestByText("""
      from typing import TypedDict

      class Movie(TypedDict, metaclass=Meta): # type: ignore
          name: str
          
      class Movie1(TypedDict, <warning descr="Specifying a metaclass is not allowed in TypedDict">metaclass=<error descr="Unresolved reference 'Meta'">Meta</error></warning>):
          name: str
    """)
  }

  private fun doTestByText(notTrimmedText: String) {
    val text = notTrimmedText.trimIndent()
    runWithLanguageLevel(LanguageLevel.getLatest()) {
      myFixture.enableInspections(inspections)
      val currentFile: PsiFile = myFixture.configureByText(PythonFileType.INSTANCE, text)
      myFixture.checkHighlighting()
      assertSdkRootsNotParsed(currentFile)
    }
  }

  companion object {
    private val inspections = listOf(PyUnresolvedReferencesInspection::class.java,
                                     PyTypeHintsInspection::class.java,
                                     PyTypeCheckerInspection::class.java,
                                     PyArgumentListInspection::class.java,
                                     PyRedeclarationInspection::class.java,
                                     PyFinalInspection::class.java,
                                     PyProtocolInspection::class.java,
                                     PyTypedDictInspection::class.java)
  }
}