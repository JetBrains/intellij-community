// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.PyVarianceInspection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.intellij.lang.annotations.Language

internal class PyVarianceTest : PyTestCase() {

  private fun doTestTypeVarVariance(variance: PyTypeVarType.Variance?, @Language("Python") text: String) {
    myFixture.configureByText(PythonFileType.INSTANCE, text.trimIndent())
    val expr = myFixture.findElementByText("expr", PyExpression::class.java)
    assertNotNull(expr)
    val context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile())
    val type = context.getType(expr!!)
    assertInstanceOf(type, PyTypeVarType::class.java)
    assertEquals(variance, (type as PyTypeVarType).getVariance())
  }


  @TestFor(issues = ["PY-80166", "PY-80167"])
  fun `test Variance obtained from TypeVar declaration`() {
    doTestTypeVarVariance(PyTypeVarType.Variance.INVARIANT, """
      from typing import TypeVar
      T = TypeVar("T")
      expr: T
      """)
    doTestTypeVarVariance(PyTypeVarType.Variance.COVARIANT, """
      from typing import TypeVar
      T_co = TypeVar("T_co", covariant=True)
      expr: T_co
      """)
    doTestTypeVarVariance(PyTypeVarType.Variance.CONTRAVARIANT, """
      from typing import TypeVar
      T_contra = TypeVar("T_contra", contravariant=True)
      expr: T_contra
      """)
    doTestTypeVarVariance(PyTypeVarType.Variance.INFER_VARIANCE, """
      from typing import TypeVar
      T_inf = TypeVar("T_inf", infer_variance=True)
      expr: T_inf
      """)
    doTestTypeVarVariance(PyTypeVarType.Variance.BIVARIANT, """
      from typing import TypeVar
      T_wrong = TypeVar("T_wrong", covariant=True, contravariant=True)
      expr: T_wrong
      """)
    doTestTypeVarVariance(PyTypeVarType.Variance.INVARIANT, """
      def foo[T]():
        expr: T
      """)
    doTestTypeVarVariance(PyTypeVarType.Variance.INFER_VARIANCE, """
      class C[T]:
        def foo(self):
          expr: T
      """)
  }


  fun doTestByText(@Language("Python") text: String) {
    val currentFile = myFixture.configureByText(PythonFileType.INSTANCE, text.trimIndent())
    myFixture.enableInspections(PyVarianceInspection::class.java)
    myFixture.checkHighlighting(true, false, true)
    assertSdkRootsNotParsed(currentFile)
  }

  @TestFor(issues = ["PY-80166"])
  fun `test Covariant TypeVars cannot be used in function parameter types`() {
    doTestByText("""
      from typing import TypeVar, Generic
      
      T_co = TypeVar('T_co', covariant=True)
      T_contra = TypeVar('T_contra', contravariant=True)
      
      def foo(x: T_co) -> None: ...
      
      class Foo(Generic[T_co]):
          def __init__(self, x: T_co) -> None: ... # allowed in __init__
          def do_smth(self, x: <warning descr="A covariant type variable cannot be used in this contravariant position">T_co</warning>) -> None: ...
      """)
  }

  @TestFor(issues = ["PY-80167"])
  fun `test Unrelated contravariant TypeVars can be used in function return type`() {
    doTestByText("""
      from typing import TypeVar, Generic
      
      T_co = TypeVar('T_co', covariant=True)
      T_contra = TypeVar('T_contra', contravariant=True)
      
      def foo(x: T_contra) -> T_contra: ...
      
      class Foo(Generic[T_co]):
          def do_smth(self, x: T_contra) -> T_contra: ...
      """)
  }

  fun `test Contravariant TypeVars cannot be used in method return type`() {
    doTestByText("""
      from typing import TypeVar, Generic
      
      T_co = TypeVar('T_co', covariant=True)
      T_contra = TypeVar('T_contra', contravariant=True)
      
      def foo(x: T_contra) -> T_contra: ...
      
      class Foo(Generic[T_contra]):
          def do_smth(self, x: T_contra) -> <warning descr="A contravariant type variable cannot be used in this covariant position">T_contra</warning>: ...
      """)
  }

  fun `test No issue for unbound type variable`() {
    doTestByText("""
      from typing import Sequence, TypeVar, Generic
      T = TypeVar("T")
      T_co = TypeVar("T_co", covariant=True)
      
      class Inv(Generic[T]):
          ...
      
      class Class1(Inv[<warning descr="A covariant type variable cannot be used in this invariant position">T_co</warning>]):
          pass
      
      inv = Inv[T_co]() # there should be no issue about incompatible variance here
      """)
  }

  @TestFor(issues = ["PY-79248"])
  fun `test Superfluous variance on constrained type variable`() {
    doTestByText("""
      from typing import TypeVar
      
      T1 = TypeVar('T1', int, str, <warning descr="Superfluous variance since the given constraints have no subtype relation">covariant=True</warning>)
      T2 = TypeVar('T2', object, str, covariant=True)  # expect no error
      """)
  }

  @TestFor(issues = ["PY-80167"])
  fun `test Warn about contravariant TypeVars used in function return type`() {
    doTestByText("""
      from typing import TypeVar, Generic
      
      T_contra = TypeVar('T_contra', contravariant=True)
      
      class Foo(Generic[T_contra]):
          def __init__(self, x: T_contra):
              pass
      
          def foo(self, x) -> <warning descr="A contravariant type variable cannot be used in this covariant position">T_contra</warning>: # False-negative
              pass
      """)
  }

  @TestFor(issues = ["PY-80167"])
  fun `test Warn about contravariant TypeVars used in function return type 2`() {
    doTestByText("""
      from typing import Callable, Generic, TypeVar
      
      in_T = TypeVar("in_T", contravariant=True)
      
      class A(Generic[in_T]):
          def f(self) -> Callable[[in_T], None]:
              ...
      """)
  }

  @TestFor(issues = ["PY-87859"])
  fun `test Variance error on frozen dataclass`() {
    doTestByText("""
      from dataclasses import dataclass
      
      @dataclass(frozen=True)
      class A[T]:
          a: T  # expect no error here
      """)
  }

  @TestFor(issues = ["PY-87913"])
  fun `test Variance ReadOnly attribute`() {
    doTestByText("""
      from typing import ReadOnly, TypeVar, TypedDict, Generic
      
      out_T = TypeVar("out_T", covariant=True)
      
      class TD(TypedDict, Generic[out_T]):
          t: ReadOnly[out_T]  # expect no error here
      """)
  }
}
