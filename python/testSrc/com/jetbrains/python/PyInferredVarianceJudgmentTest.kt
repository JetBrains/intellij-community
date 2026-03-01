// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.getInferredVariance
import com.jetbrains.python.psi.types.PyTypeVarType.Variance
import com.jetbrains.python.psi.types.TypeEvalContext
import junit.framework.AssertionFailedError
import org.intellij.lang.annotations.Language

internal class PyInferredVarianceJudgmentTest : PyTestCase() {


  private fun doTest(expression: String, expectedVariance: Variance?, @Language("Python") text: String) {
    return doTest(expression, expectedVariance, PyTypeParameter::class.java, text)
  }

  private fun doTest(expression: String, expectedVariance: Variance?, clazz: Class<out PyElement>, @Language("Python") text: String) {
    val textIndented = text.trimIndent()
    myFixture.configureByText(PythonFileType.INSTANCE, textIndented)
    val typeVar: PyElement = myFixture.findElementByText(expression, clazz)

    val context = TypeEvalContext.userInitiated(typeVar.project, typeVar.containingFile)
    val actualVariance = getInferredVariance(typeVar, context)
    assertEquals(expectedVariance, actualVariance)
  }


  // Variance is declared as non-infer variance

  fun `test Generic function with type var declared invariant`() {
    doTest("T):", Variance.INVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar
      T = TypeVar("T")
      def fn(t: T): pass
      """)
  }

  fun `test Generic function with type var declared covariant`() {
    doTest("T):", Variance.INVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar
      T = TypeVar("T", covariant=True)
      def fn(t: T): pass
      """)
  }

  fun `test Generic function with declared contravariant`() {
    doTest("T:", Variance.INVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar
      T = TypeVar("T", contravariant=True)
      def fn() -> T: pass
      """)
  }

  fun `test Generic class unused type var declared invariant`() {
    doTest("T]):", Variance.INVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T")
      class A(Generic[T]):
          def method(self): pass
      """)
  }

  fun `test Generic class unused type var declared covariant`() {
    doTest("T]):", Variance.COVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T", covariant=True)
      class A(Generic[T]):
          def method(self): pass
      """)
  }

  fun `test Generic class unused type var declared contravariant`() {
    doTest("T]):", Variance.CONTRAVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T", contravariant=True)
      class A(Generic[T]):
          def method(self): pass
      """)
  }

  // Inferring variance is necessary: Functions have no impact

  fun `test Generic function unused`() {
    doTest("T", Variance.INVARIANT, """
      def fn[T](): pass
      """)
  }

  fun `test Generic function return type`() {
    doTest("T", Variance.INVARIANT, """
      def fn[T]() -> T: pass
      """)
  }

  fun `test Generic function parameter`() {
    doTest("T", Variance.INVARIANT, """
      def fn[T](t: T): pass
      """)
  }

  fun `test Generic function parameter nesting callable parameter`() {
    doTest("T", Variance.INVARIANT, """
      from typing import Callable
      def fn[T](t: Callable[[T], None]): pass
      """)
  }

  fun `test Generic function parameter nesting callable return`() {
    doTest("T", Variance.INVARIANT, """
      from typing import Callable
      def fn[T](t: Callable[[], T]): pass
      """)
  }

  fun `test Generic function return nesting callable parameter`() {
    doTest("T", Variance.INVARIANT, """
      from typing import Callable
      def fn[T]() -> Callable[[T], None]: pass
      """)
  }

  fun `test Generic function return nesting callable return`() {
    doTest("T", Variance.INVARIANT, """
      from typing import Callable
      def fn[T]() -> Callable[[], T]: pass
      """)
  }

  // Inferring variance is necessary

  fun `test Generic class unused`() {
    // see comment about bivariance in: PyInferredVarianceJudgment.doGetInferredVariance
    doTest("T", Variance.BIVARIANT, """
      class A[T]:
          def method(self): pass
      """)
  }

  fun `test Generic sub class unused`() {
    // see comment about bivariance in: PyInferredVarianceJudgment.doGetInferredVariance
    doTest("T", Variance.BIVARIANT, """
      class A[S]:
          ...
      class B[T](A[T]):
          def method(self): pass
      """)
  }

  fun `test Generic class unused TypeVar syntax`() {
    // see comment about bivariance in: PyInferredVarianceJudgment.doGetInferredVariance
    doTest("T])", Variance.BIVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T", infer_variance=True)
      class A(Generic[T]):
          def method(self): pass
      """)
  }

  fun `test Generic protocol class unused TypeVar syntax`() {
    // see comment about bivariance in: PyInferredVarianceJudgment.doGetInferredVariance
    doTest("T])", Variance.BIVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Protocol
      T = TypeVar("T", infer_variance=True)
      class A(Protocol[T]):
          def method(self): pass
      """)
  }

  fun `test Generic class attribute`() {
    doTest("T", Variance.INVARIANT, """
      class A[T]:
          attr: T
      """)
  }

  fun `test Generic class attribute callable parameter`() {
    doTest("T", Variance.INVARIANT, """
      from typing import Callable
      class A[T]:
          attr: Callable[[T], None]
      """)
  }

  fun `test Generic class attribute callable return`() {
    doTest("T", Variance.INVARIANT, """
      from typing import Callable
      class A[T]:
          attr: Callable[[], T]
      """)
  }

  fun `test Generic class readonly attribute`() {
    doTest("T", Variance.COVARIANT, """
      from typing import ReadOnly
      class A[T]:
          attr: ReadOnly[T] # attribute
      """)
  }

  fun `test Generic class final attribute`() {
    doTest("T", Variance.COVARIANT, """
      from typing import Final
      class A[T]:
          attr: Final[T]
      """)
  }

  fun `test Generic class final attribute callable parameter`() {
    doTest("T", Variance.CONTRAVARIANT, """
      from typing import Final, Callable
      class A[T]:
          attr: Final[Callable[[T], None]]
      """)
  }

  fun `test Generic class final attribute callable return`() {
    doTest("T", Variance.COVARIANT, """
      from typing import Final, Callable
      class A[T]:
          attr: Final[Callable[[], T]]
      """)
  }

  fun `test Generic class method parameter with inherited attribute`() {
    doTest("BT", Variance.INVARIANT, """
      class A[AT]:
          attr: AT
      
      class B[BT](A[BT]):
          def method(self, t: BT): pass
      """)
  }

  fun `test Generic class method return with inherited attribute`() {
    doTest("BT", Variance.INVARIANT, """
      class A[AT]:
          attr: AT
      
      class B[BT](A[BT]):
          def method(self) -> BT: pass
      """)
  }

  fun `test Generic class with inherited attribute`() {
    doTest("BT", Variance.INVARIANT, """
      class A[AT]:
          attr: AT # AT is inferred to be invariant
      
      class B[BT](A[BT]):
          pass
      """)
  }

  fun `test Generic class method return with inherited method parameter`() {
    doTest("BT", Variance.INVARIANT, """
      class A[AT]:
          def method(self, t: AT): pass # AT is inferred to be contravariant

      class B[BT](A[BT]):
          def method(self) -> BT: pass
      """)
  }

  fun `test Generic class attribute with method parameter`() {
    doTest("T", Variance.INVARIANT, """
      class A[T]:
          attr: T
          def method(self, t: T): pass
      """)
  }

  fun `test Generic class attribute with method return`() {
    doTest("T", Variance.INVARIANT, """
      class A[T]:
          attr: T
          def method(self) -> T: pass
      """)
  }

  fun `test Generic class return type`() {
    doTest("T", Variance.COVARIANT, """
      class A[T]:
          def method(self) -> T: pass
      """)
  }

  fun `test Generic class parameter`() {
    doTest("T", Variance.CONTRAVARIANT, """
      class A[T]:
          def method(self, arg: T): pass
      """)
  }

  fun `test Generic class invariant`() {
    doTest("T", Variance.INVARIANT, """
      class A[T]:
          def method(self, arg: T) -> T: pass
      """)
  }

  fun `test Generic class nested covariant`() {
    doTest("T", Variance.COVARIANT, """
      from typing import Iterable
      class A[T]:
          def method(self) -> Iterable[T]: pass # Covariant in Covariant -> Covariant
      """)
  }

  fun `test Generic class method parameter nesting callable parameter`() {
    doTest("T", Variance.COVARIANT, """
      from typing import Callable
      class A[T]:
          def method(self, arg: Callable[[T], None]): pass # Contravariant in Contravariant -> Covariant
      """)
  }

  fun `test Generic class method parameter nesting callable return`() {
    doTest("T", Variance.CONTRAVARIANT, """
      from typing import Callable
      class A[T]:
          def method(self, arg: Callable[[], T]): pass # Covariant in Contravariant -> Contravariant
      """)
  }

  fun `test Generic class method return nesting callable parameter`() {
    doTest("T", Variance.CONTRAVARIANT, """
      from typing import Callable
      class A[T]:
          def method(self) -> Callable[[T], None]: pass # Contravariant in Covariant -> Contravariant
      """)
  }

  fun `test Generic class method return nesting callable return`() {
    doTest("T", Variance.COVARIANT, """
      from typing import Callable
      class A[T]:
          def method(self) -> Callable[[], T]: pass # Covariant in Covariant -> Covariant
      """)
  }

  fun `test Generic class method return nesting default variance class`() {
    doTest("T", Variance.COVARIANT, """
      class Box[U]:
          def method(self): pass # U is inferred to have default variance

      class A[T]:
          def method(self) -> Box[T]: pass # Covariant in Covariant -> Covariant
      """)
  }

  fun `test Generic class method return nesting covariant class`() {
    doTest("T", Variance.COVARIANT, """
      class Box[U]:
          def method_box(self) -> U: pass # U is inferred to be covariant

      class A[T]:
          def method(self) -> Box[T]: pass # Covariant in Covariant -> Covariant
      """)
  }

  fun `test Generic class method parameter nesting covariant class`() {
    doTest("T", Variance.CONTRAVARIANT, """
      class Box[U]:
          def method_box(self) -> U: pass # U is inferred to be covariant

      class A[T]:
          def method(self, p: Box[T]): pass # Covariant in Contravariant -> Contravariant
      """)
  }

  fun `test Generic class method return nesting contravariant class`() {
    doTest("T", Variance.CONTRAVARIANT, """
      class Box[U]:
          def method_box(self, arg: U): pass # U is inferred to be contravariant

      class A[T]:
          def method(self) -> Box[T]: pass # Contravariant in Covariant -> Contravariant
      """)
  }

  fun `test Generic class method parameter nesting contravariant class`() {
    doTest("T", Variance.COVARIANT, """
      class Box[U]:
          def method_box(self, arg: U): pass # U is inferred to be contravariant

      class A[T]:
          def method(self, p: Box[T]): pass # Contravariant in Contravariant -> Covariant
      """)
  }

  fun `test Generic class method parameter nesting invariant class`() {
    doTest("T", Variance.INVARIANT, """
      class Box[U]:
          attr: U # U is inferred to be invariant

      class A[T]:
          def method(self, p: Box[T]): pass # Invariant in Contravariant -> Invariant
      """)
  }

  fun `test Generic class method return nesting invariant class`() {
    doTest("T", Variance.INVARIANT, """
      class Box[U]:
          attr: U # U is inferred to be invariant

      class A[T]:
          def method(self) -> Box[T]: pass # Invariant in Covariant -> Invariant
      """)
  }

  fun `test Nesting contravariant in invariant`() {
    doTest("T", Variance.INVARIANT, """
      class Box[U]:
          def method_box(self, arg: U) -> U: pass # U is inferred to be invariant

      class A[T]:
          def method(self) -> Box[T]: pass
      """)
  }

  fun `test Nesting contravariant in covariant`() {
    doTest("T", Variance.CONTRAVARIANT, """
      class Box[U]:
          def method_box(self, arg: U): pass # Contravariant

      class User[T]:
          def method(self) -> Box[T]: pass
      """)
  }

  fun `test Generic instance function`() {
    doTest("T])", Variance.CONTRAVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T", infer_variance=True)
      class Box(Generic[T]):
          def method(self, t: T): pass
      """)
  }

  fun `test Multiple generic methods on same type var C1`() {
    doTest("T]):#C1", Variance.CONTRAVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T", infer_variance=True)
      class C1(Generic[T]):#C1
          def method1(self, t: T): pass
      class C2(Generic[T]):#C2
          def method2(self) -> T: pass
      """)
  }

  fun `test Multiple generic methods on same type var C2`() {
    doTest("T]):#C2", Variance.COVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T", infer_variance=True)
      class C1(Generic[T]):#C1
          def method1(self, t: T): pass
      class C2(Generic[T]):#C2
          def method2(self) -> T: pass
      """)
  }

  fun `test multiple generic methods on same type var with subtype`() {
    doTest("T]):#C2", Variance.INVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T", infer_variance=True)
      class C1(Generic[T]):#C1
          def method1(self, t: T): pass
      class C2(C1[T], Generic[T]):#C2
          def method2(self) -> T: pass
      """)
  }

  fun `test Default variance class with covariant subtype`() {
    doTest("T", Variance.COVARIANT, """
      class Box[U]:
          def method(self): pass # U is inferred to have default variance

      class A[T](Box[T]):
          def method(self) -> T: pass
      """)
  }

  fun `test Default variance class with contravariant subtype`() {
    doTest("T", Variance.CONTRAVARIANT, """
      class Box[U]:
          def method(self): pass # U is inferred to have default variance

      class A[T](Box[T]):
          def method(self, t: T): pass
      """)
  }

  fun `test Generic method and unrelated function on same type var`() {
    doTest("T]):", Variance.CONTRAVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T", infer_variance=True)
      class C1(Generic[T]):
          def method1(self, t: T): pass
      
      def fn2() -> T: # does not change the inferred variance
          pass
      """)
  }

  fun `test Generic instance functions and unbound instance function on same type var`() {
    doTest("T]):", Variance.CONTRAVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T", infer_variance=True)
      class C(Generic[T]):
          def method1(self, t: T): pass
          def fn() -> T: pass # does not change the inferred variance
      """)
  }

  fun `test Generic instance functions and class method on same type var`() {
    doTest("T]):", Variance.CONTRAVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T", infer_variance=True)
      class C(Generic[T]):
          def method1(self, t: T): pass
          @classmethod
          def fn(cls) -> T: pass # does not change the inferred variance
      """)
  }

  fun `test Generic instance functions and static method on same type var`() {
    doTest("T]):", Variance.CONTRAVARIANT, PyReferenceExpression::class.java, """
      from typing import TypeVar, Generic
      T = TypeVar("T", infer_variance=True)
      class C(Generic[T]):
          def method1(self, t: T): pass
          @staticmethod
          def fn() -> T: pass # does not change the inferred variance
      """)
  }

  fun `test Generic instance functions and unbound instance function on same type var using new syntax`() {
    doTest("U]:", Variance.CONTRAVARIANT, """
      class D[U]:
          def method1(self, u: U): pass
          @staticmethod
          def fn() -> U: pass # does not change the inferred variance
      """)
  }

  fun `test Generic instance functions and class method on same type var using new syntax`() {
    doTest("U]:", Variance.CONTRAVARIANT, """
      class D[U]:
          def method1(self, u: U): pass
          @classmethod
          def fn(cls) -> U: pass # does not change the inferred variance
      """)
  }

  fun `test Generic instance functions and static method on same type var using new syntax`() {
    doTest("U]:", Variance.CONTRAVARIANT, """
      class D[U]:
          def method1(self, u: U): pass
          @staticmethod
          def fn() -> U: pass # does not change the inferred variance
      """)
  }

  fun `test Covariant method and __init__ method`() {
    doTest("T]:", Variance.COVARIANT, """
      class A[T]:  # covariant
          def __init__(self, t: T): ...
          
          def f(self) -> T: ...
      """)
  }

  fun `test Contravariant method and __init__ method`() {
    doTest("T]:", Variance.CONTRAVARIANT, """
      class A[T]:
          def __init__(self, t: T): ...
          
          def f(self, T: T): ...
      """)
  }

  fun `test Covariant method and __new__ method`() {
    doTest("T]:", Variance.COVARIANT, """
      class A[T]:
          def __new__(self, t: T): ...
          
          def f(self) -> T: ...
      """)
  }

  fun `test Contravariant method and __new__ method`() {
    doTest("T]:", Variance.CONTRAVARIANT, """
      class A[T]:
          def __new__(self, t: T): ...
          
          def f(self, T: T): ...
      """)
  }

  fun `test Private attributes are ignored`() {
    doTest("T]:", Variance.BIVARIANT, """
      class A[T]:
          __t: T  # private
      """)
  }

  fun `test Private methods are ignored`() {
    doTest("T]:", Variance.BIVARIANT, """
      class A[T]:
          def __foo(self, t:T) -> T: pass  # private
      """)
  }

  fun `test Protected attributes are ignored`() {
    doTest("T]:", Variance.BIVARIANT, """
      class A[T]:
          _t: T  # protected
      """)
  }

  fun `test Protected methods are ignored`() {
    doTest("T]:", Variance.BIVARIANT, """
      class A[T]:
          def _foo(self, t:T) -> T: pass  # private
      """)
  }

  fun `test Sunder members are not ignored`() {
    doTest("T]:", Variance.INVARIANT, """
      class A[T]:
          _attr_ : T  # sunder attribute
          def _method_(self, t:T) -> T: pass  # sunder method
      """)
  }

  fun `test Dunder members are not ignored`() {
    doTest("T]:", Variance.INVARIANT, """
      class A[T]:
          __attr__ : T  # dunder attribute
          def __method__(self, t:T) -> T: pass  # dunder method
      """)
  }

  fun `test Implicit generic attributes are ignored`() {
    // Note that body information availability depends on the capability of the context:
    // For local files it is available, but for other files it is not since they rely on stub information.
    // That behavior would make variance inference flaky.
    doTest("T]:", Variance.BIVARIANT, """
      class A[T]:
          def __init__(self, t: T):
              self.t = t # introduction of public attribute without class declaration
      """)
  }

  fun `test Implicit generic return types are ignored`() {
    // Note that inferred return types depend on the capability of the context:
    // For local files it can be inferred, but for other files it will be inferred to Any.
    // That behavior would make variance inference flaky.
    doTest("T]:", Variance.BIVARIANT, """
      class A[T]:
          __t: T  # no effect on inferred variance
          def foo(self):
              return __t # returned type is T
      """)
  }

  fun `test Frozen attribute`() {
    doTest("T]:", Variance.COVARIANT, """
      from dataclasses import dataclass
      @dataclass(frozen=True)
      class A[T]:
          attr: T  # read-only
      """)
  }

  fun `test Frozen attribute callable parameter`() {
    doTest("T]:", Variance.CONTRAVARIANT, """
      from typing import Callable
      from dataclasses import dataclass
      @dataclass(frozen=True)
      class A[T]:
          attr: Callable[[T], None]  # read-only
      """)
  }

  fun `test Frozen attribute callable return`() {
    doTest("T]:", Variance.COVARIANT, """
      from typing import Callable
      from dataclasses import dataclass
      @dataclass(frozen=True)
      class A[T]:
          attr: Callable[[], T]  # read-only
      """)
  }

  fun `test Alias to contravariant class`() {
    doTest("U]", Variance.CONTRAVARIANT, """
      class A[T]:
          def f(self, t: T): pass
      type B[U] = A[U]
      """)
  }

  fun `test Alias to covariant class`() {
    doTest("U]", Variance.COVARIANT, """
      class A[T]:
          def f(self) -> T: pass
      type B[U] = A[U]
      """)
  }

  fun `test Alias to union of class`() {
    doTest("U]", Variance.INVARIANT, """
      class A[S]:
          def f(self, t: S): pass
      class B[T]:
          def f(self) -> T: pass
      type C[U] = A[U] | B[U]
      """)
  }

  fun `test Alias to tuple`() {
    doTest("T]", Variance.COVARIANT, """
      type A[T] = tuple[T]
      """)
  }

  fun `test Generic function with type var declared via custom function`() {
    doTest("X):", null, PyReferenceExpression::class.java, """
      from typing import TypeVar
      T = TypeVar("T")

      def make_tv(): return T
      
      X = make_tv()
      
      # We cannot infer variance for type variables whose origin was disguised
      def fn(t: X): pass
      """)
  }

  fun `test Type in string literal`() {
    fixme("PY-87942: No AST in string literal of type annotation", AssertionFailedError::class.java) {
      doTest("T", Variance.COVARIANT, """
        class A[T]:
            def method(self) -> "T": pass
        """)
    }
  }

  fun `test Type in string literal with Callable`() {
    fixme("PY-87942: No AST in string literal of type annotation", AssertionFailedError::class.java) {
      doTest("T", Variance.COVARIANT, """
      from typing import Callable
      class A[T]:
          def method(self, arg: "Callable[[T], None]"): pass
      """)
    }
  }

  fun `test Recursive generic classes`() {
    doTest("T", Variance.COVARIANT, """
      class A[T]:
          def method(self) -> B[T]: pass

      class B[U]:
          def method(self) -> A[U]: pass
      """)
  }
}
