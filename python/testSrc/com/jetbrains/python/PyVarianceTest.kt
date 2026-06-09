// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.openapi.application.ReadAction
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.PyTypeParameterType
import com.jetbrains.python.psi.types.PyTypeParameterType.Variance.BIVARIANT
import com.jetbrains.python.psi.types.PyTypeParameterType.Variance.CONTRAVARIANT
import com.jetbrains.python.psi.types.PyTypeParameterType.Variance.COVARIANT
import com.jetbrains.python.psi.types.PyTypeParameterType.Variance.INFER_VARIANCE
import com.jetbrains.python.psi.types.PyTypeParameterType.Variance.INVARIANT
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PyVarianceTest : PyCodeInsightTestCase() {

  private fun doTestTypeVarVariance(variance: PyTypeParameterType.Variance?, @Language("Python") text: String) {
    myFixture.configureByText(PythonFileType.INSTANCE, text.trimIndent())
    ReadAction.runBlocking<Throwable> {
      val expr = myFixture.findElementByText("expr", PyExpression::class.java)
      Assertions.assertNotNull(expr)
      val context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile())

      val type = context.getType(expr!!)
      Assertions.assertInstanceOf(PyTypeVarType::class.java, type)
      Assertions.assertEquals(variance, (type as PyTypeVarType).variance)
    }
  }

  @Test
  @TestFor(issues = ["PY-80166", "PY-80167"])
  fun `Variance obtained from TypeVar declaration`() {
    doTestTypeVarVariance(INVARIANT, """
      from typing import TypeVar
      T = TypeVar("T")
      expr: T
      """.trimIndent())
    doTestTypeVarVariance(COVARIANT, """
      from typing import TypeVar
      T_co = TypeVar("T_co", covariant=True)
      expr: T_co
      """.trimIndent())
    doTestTypeVarVariance(CONTRAVARIANT, """
      from typing import TypeVar
      T_contra = TypeVar("T_contra", contravariant=True)
      expr: T_contra
      """.trimIndent())
    doTestTypeVarVariance(INFER_VARIANCE, """
      from typing import TypeVar
      T_inf = TypeVar("T_inf", infer_variance=True)
      expr: T_inf
      """.trimIndent())
    doTestTypeVarVariance(BIVARIANT, """
      from typing import TypeVar
      T_wrong = TypeVar("T_wrong", covariant=True, contravariant=True)
      expr: T_wrong
      """.trimIndent())
    doTestTypeVarVariance(INVARIANT, """
      def foo[T]():
        expr: T
      """.trimIndent())
    doTestTypeVarVariance(INFER_VARIANCE, """
      class C[T]:
        def foo(self):
          expr: T
      """.trimIndent())
  }

  @Test
  @TestFor(issues = ["PY-80166"])
  fun `Covariant TypeVars cannot be used in function parameter types`() = test("""
    from typing import TypeVar, Generic
    
    T_co = TypeVar('T_co', covariant=True)
    T_contra = TypeVar('T_contra', contravariant=True)
    
    def foo(x: T_co) -> None: ...
    
    class Foo(Generic[T_co]):
        def __init__(self, x: T_co) -> None: ... # allowed in __init__
        def do_smth(self, x: T_co) -> None: ...
    #                        ^^^^ WARNING A covariant type variable cannot be used in this contravariant position
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-80167"])
  fun `Unrelated contravariant TypeVars can be used in function return type`() = test("""
    from typing import TypeVar, Generic
    
    T_co = TypeVar('T_co', covariant=True)
    T_contra = TypeVar('T_contra', contravariant=True)
    
    def foo(x: T_contra) -> T_contra: ...
    
    class Foo(Generic[T_co]):
        def do_smth(self, x: T_contra) -> T_contra: ...
    """.trimIndent())

  @Test
  fun `Contravariant TypeVars cannot be used in method return type`() = test("""
    from typing import TypeVar, Generic
    
    T_co = TypeVar('T_co', covariant=True)
    T_contra = TypeVar('T_contra', contravariant=True)
    
    def foo(x: T_contra) -> T_contra: ...
    
    class Foo(Generic[T_contra]):
        def do_smth(self, x: T_contra) -> T_contra: ...
    #                                     ^^^^^^^^ WARNING A contravariant type variable cannot be used in this covariant position
    """.trimIndent())

  @Test
  fun `No issue for unbound type variable`() = test("""
    from typing import Sequence, TypeVar, Generic
    T = TypeVar("T")
    T_co = TypeVar("T_co", covariant=True)
    
    class Inv(Generic[T]):
        ...
    
    class Class1(Inv[T_co]):
    #                ^^^^ WARNING A covariant type variable cannot be used in this invariant position
        pass
    
    inv = Inv[int]() # there should be no issue about incompatible variance here
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-79248"])
  fun `Superfluous variance on constrained type variable`() = test("""
    from typing import TypeVar
    
    T1 = TypeVar('T1', int, str, covariant=True)
    #                            ^^^^^^^^^^^^^^ WARNING Superfluous variance since the given constraints have no subtype relation
    T2 = TypeVar('T2', object, str, covariant=True)  # expect no error
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-80167"])
  fun `Warn about contravariant TypeVars used in function return type`() = test("""
    from typing import TypeVar, Generic
    
    T_contra = TypeVar('T_contra', contravariant=True)
    
    class Foo(Generic[T_contra]):
        def __init__(self, x: T_contra):
            pass
    
        def foo(self, x) -> T_contra:
    #                       ^^^^^^^^ WARNING A contravariant type variable cannot be used in this covariant position
            pass
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-80167"])
  fun `Warn about contravariant TypeVars used in function return type 2`() = test("""
    from typing import Callable, Generic, TypeVar
    
    in_T = TypeVar("in_T", contravariant=True)
    
    class A(Generic[in_T]):
        def f(self) -> Callable[[in_T], None]:
            ...
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-87859"])
  fun `Variance error on frozen dataclass`() = test("""
    from dataclasses import dataclass
    
    @dataclass(frozen=True)
    class A[T]:
        a: T  # expect no error here
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-87913"])
  fun `Variance ReadOnly attribute`() = test("""
    from typing import ReadOnly, TypeVar, TypedDict, Generic
    
    out_T = TypeVar("out_T", covariant=True)
    
    class TD(TypedDict, Generic[out_T]):
        t: ReadOnly[out_T]  # expect no error here
    """.trimIndent())

  @Test
  fun `Type alias for generic class covariant`() = test("""
    from typing import Generic, TypeVar, TypeAlias
    T = TypeVar("T")
    class ClassA(Generic[T]): ...
    
    T_co = TypeVar("T_co", covariant=True)
    A_Alias_1: TypeAlias = ClassA[T_co]
    
    class ClassA_2(A_Alias_1[T_co]): ...
    #                        ^^^^ WARNING A covariant type variable cannot be used in this invariant position
    """.trimIndent())

  @Test
  fun `Protocol declaration variance is inferred as covariant from protocol return type`() = test("""
    from typing import Protocol, TypeVar
    T = TypeVar("T")
    
    class ReturnsValue(Protocol[T]):
    #                           └ WARNING This type variable is effectively covariant in this protocol, so it cannot be invariant here
        def get(self) -> T: ...
    """.trimIndent())

  @Test
  fun `Protocol declaration variance is inferred as covariant from protocol return type quoted return type`() = test("""
    from typing import Protocol, TypeVar
    T = TypeVar("T")
    
    class ReturnsValue(Protocol[T]):
    #                           └ WARNING This type variable is effectively covariant in this protocol, so it cannot be invariant here
        def get(self) -> "T": ...
    """.trimIndent())

  @Test
  fun `Protocol declaration variance is inferred as covariant from protocol return type 2`() = test("""
    from typing import Protocol, TypeVar
    T = TypeVar("T", covariant=True)
    
    class ReturnsValue(Protocol[T]):
        def get(self) -> T: ...
    """.trimIndent())

  @Test
  fun `Protocol declaration variance is inferred for later type parameters`() = test("""
    from typing import Protocol, TypeVar
    
    T1 = TypeVar("T1")
    T2= TypeVar("T2")
    
    class P(Protocol[T1, T2]):
    #                    ^^ WARNING This type variable is effectively contravariant in this protocol, so it cannot be invariant here
        def use_first(self, value: T1) -> T1: ...
        def use_second(self, value: T2) -> None: ...
    """.trimIndent())

  @Test
  fun `Protocol declaration variance is inferred for complex type`() = test("""
    from typing import Protocol, TypeVar
    
    T1 = TypeVar("T1", contravariant=True)
    
    class P(Protocol[T1]):
    #                ^^ WARNING This type variable is effectively invariant in this protocol, so it cannot be contravariant here
        def foo(self, value: list[T1]) -> None: ...
    #                             ^^ WARNING A contravariant type variable cannot be used in this invariant position
    """.trimIndent())

  @Test
  fun `Protocol declaration variance is inferred for quoted complex type`() = test("""
    from typing import Protocol, TypeVar
    
    T1 = TypeVar("T1", contravariant=True)
    
    class P(Protocol[T1]):
    #                ^^ WARNING This type variable is effectively invariant in this protocol, so it cannot be contravariant here
      def foo(self, value: "list[T1]") -> None: ...
    #                      ^^^^^^^^^^ WARNING A contravariant type variable cannot be used in this invariant position
    """.trimIndent())

  @Test
  fun `Protocol declaration variance is inferred as contravariant from protocol parameter`() = test("""
    from typing import Protocol, TypeVar
    
    T = TypeVar("T")
    
    class AcceptsValue(Protocol[T]):
    #                           └ WARNING This type variable is effectively contravariant in this protocol, so it cannot be invariant here
        def put(self, value: T) -> None: ...
    """.trimIndent())

  @Test
  fun `Protocol declaration variance is inferred as contravariant from protocol parameter 2`() = test("""
    from typing import Protocol, TypeVar
    
    T = TypeVar("T", contravariant=True)
    
    class AcceptsValue(Protocol[T]):
        def put(self, value: T) -> None: ...
    """.trimIndent())

  @Test
  fun `Protocol declaration variance ignores init-only members`() = test("""
    from typing import Protocol, TypeVar
    
    T = TypeVar("T")
    
    class InitOnly(Protocol[T]):
    #                       └ WARNING This type variable is effectively covariant in this protocol, so it cannot be invariant here
        def __init__(self, value: T) -> None: ...
    """.trimIndent())

  @Test
  fun `Protocol declaration variance ignores init-only members 2`() = test("""
    from typing import Protocol, TypeVar
    
    T = TypeVar("T", covariant=True)
    #\ TYPE TypeVar
    
    class InitOnly(Protocol[T]):
        def __init__(self, value: T) -> None: ...
    """.trimIndent())

  @Test
  fun `Protocol declaration variance ignores fixed tuple covariance`() = test("""
    from typing import Protocol, TypeVar
    
    S = TypeVar("S")
    T = TypeVar("T")
    
    class PairProto(Protocol[S, T]):
        def method1(self, a: S, b: T) -> tuple[S, T]: ...
    """.trimIndent())

  @Test
  fun `Protocol declaration variance tuple expression`() = test("""
    from typing import Protocol, TypeVar
    
    S = TypeVar("S")
    T = TypeVar("T")
    
    class PairProto(Protocol[S, T]):
    #                        │  └ WARNING This type variable is effectively covariant in this protocol, so it cannot be invariant here
    #                        └ WARNING This type variable is effectively covariant in this protocol, so it cannot be invariant here
        def method1(self) -> tuple[S, T]: ...
    """.trimIndent())

  @Test
  fun `Recursive protocol variance`() = test("""
    from typing import Protocol, TypeVar
    
    T_co = TypeVar("T_co", covariant=True)
    T_contra = TypeVar("T_contra", contravariant=True)
    
    class RecursiveProto(Protocol[T_co, T_contra]):
        def method1(self) -> "RecursiveProto[T_co, T_contra]": ...
    
        @classmethod
        def method2(cls, value: T_contra) -> None: ...
    """.trimIndent())

  @Test
  fun `No inspection issues on type arguments of self type`() = test("""
    class K[T]:
        def m1(self: K[T], x: T) -> None: ...
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-88677"])
  fun `Show error inside quoted type annotation`() = test("""
    from typing import TypeVar, Generic, Callable
    
    T_co = TypeVar("T_co", covariant=True)
    
    class C(Generic[T_co]):
            def method1(self) -> "Callable[[T_co], None]": ...
    #                            ^^^^^^^^^^^^^^^^^^^^^^^^ WARNING A covariant type variable cannot be used in this contravariant position
    """.trimIndent())

}
