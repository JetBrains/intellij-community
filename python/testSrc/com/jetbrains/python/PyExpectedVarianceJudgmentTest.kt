// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.PyExpectedVarianceJudgment.getExpectedVariance
import com.jetbrains.python.psi.types.PyTypeVarType.Variance
import com.jetbrains.python.psi.types.TypeEvalContext
import junit.framework.AssertionFailedError
import org.intellij.lang.annotations.Language

internal class PyExpectedVarianceJudgmentTest : PyTestCase() {

  private fun doTest(expression: String, expectedVariance: Variance?, @Language("Python") text: String) {
    val textIndented = text.trimIndent()
    myFixture.configureByText(PythonFileType.INSTANCE, textIndented)
    val typeAnnotation: PyExpression = myFixture.findElementByText(expression, PyExpression::class.java)

    val context = TypeEvalContext.userInitiated(typeAnnotation.project, typeAnnotation.containingFile)
    val actualVariance = getExpectedVariance(typeAnnotation, context)
    assertEquals(expectedVariance, actualVariance)
  }

  fun `test Generic super class expects bivariant type parameters`() {
    doTest("T]", Variance.BIVARIANT, """
      from typing import TypeVar, Generic
      T = TypeVar("T")
      class C(Generic[T]):
          pass
      """)
  }

  fun `test Generic super class expects bivariant type parameters co`() {
    doTest("T1]", Variance.BIVARIANT, """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1", covariant=True)
      class Box(Generic[T1]):
          pass
      """)
  }

  fun `test Generic super class expects bivariant type parameters contra`() {
    doTest("T1]", Variance.BIVARIANT, """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1", contravariant=True)
      class Box(Generic[T1]):
          pass
      """)
  }

  fun `test Protocol super class expects bivariant type parameters`() {
    doTest("T]", Variance.BIVARIANT, """
      from typing import TypeVar, Protocol
      T = TypeVar("T")
      class C(Protocol[T]):
          pass
      """)
  }

  fun `test Generic class attribute`() {
    doTest("T #", Variance.INVARIANT, """
      class A[T]:
          attr: T # attribute
      """)
  }

  fun `test Generic class attribute callable parameter`() {
    doTest("T],", Variance.INVARIANT, """
      from typing import Callable
      class A[T]:
          attr: Callable[[T], None]
      """)
  }

  fun `test Generic class attribute callable return`() {
    doTest("T] #", Variance.INVARIANT, """
      from typing import Callable
      class A[T]:
          attr: Callable[[], T] # attribute
      """)
  }

  fun `test Generic class readonly attribute`() {
    doTest("T] #", Variance.COVARIANT, """
      from typing import ReadOnly
      class A[T]:
          attr: ReadOnly[T] # attribute
      """)
  }

  fun `test Generic class final attribute`() {
    doTest("T] #", Variance.COVARIANT, """
      from typing import Final
      class A[T]:
          attr: Final[T] # attribute
      """)
  }

  fun `test Generic class final attribute callable parameter`() {
    doTest("T],", Variance.CONTRAVARIANT, """
      from typing import Final, Callable
      class A[T]:
          attr: Final[Callable[[T], None]]
      """)
  }

  fun `test Generic class final attribute callable return`() {
    doTest("T]]", Variance.COVARIANT, """
      from typing import Final, Callable
      class A[T]:
          attr: Final[Callable[[], T]]
      """)
  }

  fun `test Generic class method parameter`() {
    doTest("T)", Variance.CONTRAVARIANT, """
      class A[T]:
          def method(self, t: T): pass
      """)
  }

  fun `test Generic class method return type`() {
    doTest("T:", Variance.COVARIANT, """
      class A[T]:
          def method(self) -> T: pass
      """)
  }

  fun `test Generic class nested covariant`() {
    doTest("T]: pass", Variance.COVARIANT, """
      from typing import Iterable
      class A[T]:
          def method(self) -> Iterable[T]: pass
      """)
  }

  fun `test Generic class method parameter nesting callable parameter`() {
    doTest("T],", Variance.COVARIANT, """
      from typing import Callable
      class A[T]:
          def method(self, arg: Callable[[T], None]): pass
      """)
  }

  fun `test Generic class method parameter nesting callable return`() {
    doTest("T])", Variance.CONTRAVARIANT, """
      from typing import Callable
      class A[T]:
          def method(self, arg: Callable[[], T]): pass
      """)
  }

  fun `test Generic class method return nesting callable parameter`() {
    doTest("T],", Variance.CONTRAVARIANT, """
      from typing import Callable
      class A[T]:
          def method(self) -> Callable[[T], None]: pass
      """)
  }

  fun `test Generic class method return nesting callable return`() {
    doTest("T]: pass", Variance.COVARIANT, """
      from typing import Callable
      class A[T]:
          def method(self) -> Callable[[], T]: pass
      """)
  }

  fun `test Generic class type argument legacy syntax 1`() {
    doTest("T2]", Variance.INVARIANT, """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1", infer_variance=False)
      class Box(Generic[T1]):
          pass
      T2 = TypeVar('T2', contravariant=True)
      class ReadOnlyBox(Box[T2], Generic[T2]):
          pass
      """)
  }

  fun `test Generic class type argument legacy syntax 1a`() {
    doTest("T2]", Variance.COVARIANT, """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1", covariant=True)
      class Box(Generic[T1]):
          pass
      T2 = TypeVar('T2', contravariant=True)
      class ReadOnlyBox(Box[T2], Generic[T2]):
          pass
      """)
  }

  fun `test Generic class type argument legacy syntax 1b`() {
    doTest("T2]", Variance.INVARIANT, """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1", contravariant=False)
      class Box(Generic[T1]):
          pass
      T2 = TypeVar('T2', covariant=True)
      class ReadOnlyBox(Box[T2], Generic[T2]):
          pass
      """)
  }

  fun `test Generic class type argument legacy syntax 2a`() {
    doTest("T3,", Variance.INVARIANT, """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1", infer_variance=False)
      T2 = TypeVar("T2", infer_variance=False)
      class Box(Generic[T1, T2]):
          pass
  
      T3 = TypeVar("T3", contravariant=True)
      T4 = TypeVar("T4", contravariant=True)
      class ReadOnlyBox(Box[T3, T4]):
          pass
      """)
  }

  fun `test Generic class type argument legacy syntax 2b`() {
    doTest("T4]", Variance.INVARIANT, """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1", infer_variance=False)
      T2 = TypeVar("T2", infer_variance=False)
      class Box(Generic[T1, T2]):
          pass
  
      T3 = TypeVar("T3", contravariant=True)
      T4 = TypeVar("T4", contravariant=True)
      class ReadOnlyBox(Box[T3, T4]):
          pass
      """)
  }

  fun `test Generic class type argument PEP695 syntax`() {
    doTest("T2]", Variance.BIVARIANT, """
      from typing import TypeVar, Generic
      class Box[T1]:
          pass
      T2 = TypeVar('T2', contravariant=True)
      class ReadOnlyBox(Box[T2], Generic[T2]):
          pass
      """)
  }

  fun `test Generic class type argument PEP695 syntax 2a`() {
    doTest("T3,", Variance.BIVARIANT, """
      from typing import TypeVar, Generic
      class Box[T1, T2]:
          pass
  
      T3 = TypeVar("T3", contravariant=True)
      T4 = TypeVar("T4", contravariant=True)
      class ReadOnlyBox(Box[T3, T4]):
          pass
      """)
  }

  fun `test Generic class type argument PEP695 syntax 2b`() {
    doTest("T4]", Variance.BIVARIANT, """
      from typing import TypeVar, Generic
      class Box[T1, T2]:
          pass
  
      T3 = TypeVar("T3", contravariant=True)
      T4 = TypeVar("T4", contravariant=True)
      class ReadOnlyBox(Box[T3, T4]):
          pass
      """)
  }

  fun `test Nested generic classes invariant`() {
    doTest("T]", Variance.CONTRAVARIANT, """
      from typing import TypeVar, Generic
      T = TypeVar("T")
      T_co = TypeVar("T_co", covariant=True)
      T_contra = TypeVar("T_contra", contravariant=True)
      class Co(Generic[T_co]):
          pass
      class Contra(Generic[T_contra]):
          pass
      class A(Contra[Co[T]]):
          pass
      """)
  }

  fun `test Nested generic classes covariant`() {
    doTest("T]", Variance.COVARIANT, """
      from typing import TypeVar, Generic
      T = TypeVar("T")
      T_co = TypeVar("T_co", covariant=True)
      T_contra = TypeVar("T_contra", contravariant=True)
      class Co(Generic[T_co]):
          pass
      class Contra(Generic[T_contra]):
          pass
      class A(Co[Co[T]]):
          pass
      """)
  }

  fun `test Nested generic classes contravariant`() {
    doTest("T]", Variance.COVARIANT, """
      from typing import TypeVar, Generic
      T = TypeVar("T")
      T_co = TypeVar("T_co", covariant=True)
      T_contra = TypeVar("T_contra", contravariant=True)
      class Co(Generic[T_co]):
          pass
      class Contra(Generic[T_contra]):
          pass
      class A(Contra[Contra[T]]):
          pass
      """)
  }

  fun `test Frozen attribute`() {
    doTest("T  #", Variance.COVARIANT, """
      from dataclasses import dataclass
      @dataclass(frozen=True)
      class A[T]:
          attr: T  # read-only
      """)
  }

  fun `test String literal type`() {
    doTest("T\"  #", Variance.COVARIANT, """
      from dataclasses import dataclass
      @dataclass(frozen=True)
      class A[T]:
          attr: "T"  # read-only
      """)
  }

  fun `test String literal type at return`() {
    fixme("PY-87942: No AST in string literal of type annotation", AssertionFailedError::class.java) {
      doTest("T\"", Variance.COVARIANT, """
        class A[T]:
            def f(self, t: Callable[["T"],None]) : ...
        """)
    }
  }

  // Expect null to avoid variance compatibility inspection check

  fun `test Type alias for generic class`() {
    doTest("T2]", null, """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1", covariant=True)
      class Box(Generic[T1]):
          pass
      Box_TA = Box[T1]
      T2 = TypeVar("T2", covariant=True)
      my_box: Box_TA[T2]
      """)
  }

  fun `test Generic class dunder init special case`() {
    // actually bivariant
    doTest("T):", null, """
      class A[T]:
          def __init__(self, value: T): pass
      """)
  }

  fun `test Generic class dunder new special case`() {
    // actually bivariant
    doTest("T):", null, """
      class A[T]:
          def __new__(self, value: T): pass
      """)
  }

  fun `test Generic class dunder init safety`() {
    doTest("int", null, """
      class A[T]:
          def __init__(self, value: T) -> int : pass
      """)
  }

  fun `test Private attributes are ignored`() {
    doTest("T  #", null, """
      class A[T]:
          __t: T  # private
      """)
  }

  fun `test Private methods are ignored`() {
    doTest("T)", null, """
      class A[T]:
          def __foo(self, t:T) -> T: pass  # private
      """)
  }

  fun `test Protected attributes are ignored`() {
    doTest("T  #", null, """
      class A[T]:
          _t: T  # protected
      """)
  }

  fun `test Protected methods are ignored`() {
    doTest("T)", null, """
      class A[T]:
          def _foo(self, t:T) -> T: pass  # private
      """)
  }

  fun `test Null when bound to function return 1`() {
    doTest("T:", null, """
      from typing import TypeVar
      T = TypeVar("T", covariant=True)
      def fn() -> T: pass
      """)
  }

  fun `test Null when bound to function return 2`() {
    doTest("T:", null, """
      def fn[T]() -> T: pass
      """)
  }

  fun `test Null when bound to function param 1`() {
    doTest("T):", null, """
      from typing import TypeVar
      T = TypeVar("T", covariant=True)
      def fn(t: T): pass
      """)
  }

  fun `test Null when bound to function param 2`() {
    doTest("T):", null, """
      def fn[T](t: T): pass
      """)
  }

  fun `test Null when bound to function parameter nesting callable parameter`() {
    doTest("T],", null, """
      from typing import Callable
      def fn[T](t: Callable[[T], None]): pass
      """)
  }

  fun `test Null when bound to function parameter nesting callable return`() {
    doTest("T])", null, """
      from typing import Callable
      def fn[T](t: Callable[[], T]): pass
      """)
  }

  fun `test Null when bound to function return nesting callable parameter`() {
    doTest("T],", null, """
      from typing import Callable
      def fn[T]() -> Callable[[T], None]: pass
      """)
  }

  fun `test Null when bound to function return nesting callable return`() {
    doTest("T]:", null, """
      from typing import Callable
      def fn[T]() -> Callable[[], T]: pass
      """)
  }

  fun `test Null when bound to function generic parameter`() {
    doTest("B_co]", null, """
      from typing import TypeVar
      B_co = TypeVar("B_co", covariant=True)
      def func(x: list[B_co]) -> B_co:
          ...
      """)
  }

  fun `test Null when in function`() {
    doTest("T:", null, """
      from typing import TypeVar, Generic
      T = TypeVar("T", covariant=True)
      def fn() -> T: pass
      """)
  }

  fun `test Null when in unbound instance function`() {
    doTest("T:", null, """
      from typing import TypeVar, Generic
      T = TypeVar("T", covariant=True)
      class C(Generic[T]):
          def fn() -> T: pass
      """)
  }

  fun `test Null when in class function`() {
    doTest("T:", null, """
      from typing import TypeVar, Generic
      T = TypeVar("T", covariant=True)
      class C(Generic[T]):
          @classmethod
          def fn(cls) -> T: pass
      """)
  }

  fun `test Null when in static function`() {
    doTest("T:", null, """
      from typing import TypeVar, Generic
      T = TypeVar("T", covariant=True)
      class C(Generic[T]):
          @staticmethod
          def fn() -> T: pass
      """)
  }

  fun `test Null when pass`() {
    doTest("pass", null, """
      class C[T]:
          def method(self) -> T: pass
      """)
  }

  fun `test Null when default parameter value`() {
    doTest("1", null, """
      class C[T]:
          def method(self, a = 1) -> T: pass
      """)
  }

  fun `test Null when literal expression`() {
    doTest("2", null, """
      class C[T]:
          def method(self, a: int) -> T:
              return a+2
      """)
  }

  fun `test Null when ref in some expression`() {
    doTest("a+", null, """
      class C[T]:
          def method(self, a: int) -> T:
              return a+2
      """)
  }

  fun `test Null when ref in attr initializer`() {
    doTest("None", null, """
      class A[T]:
          attr: T = None
      """)
  }

}
