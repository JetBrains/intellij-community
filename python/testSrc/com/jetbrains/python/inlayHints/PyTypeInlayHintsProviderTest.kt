// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.inlayHints.PyTypeInlayHintsProvider.Companion.FUNCTION_RETURN_TYPE_OPTION_ID
import com.jetbrains.python.inlayHints.PyTypeInlayHintsProvider.Companion.PARAMETER_TYPE_ANNOTATION
import com.jetbrains.python.inlayHints.PyTypeInlayHintsProvider.Companion.REVEAL_TYPE_OPTION_ID
import com.jetbrains.python.inlayHints.PyTypeInlayHintsProvider.Companion.SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID
import com.jetbrains.python.inlayHints.PyTypeInlayHintsProvider.Companion.SOLVED_FUNCTION_TYPE_PARAMETERS_OPTION_ID
import com.jetbrains.python.inlayHints.PyTypeInlayHintsProvider.Companion.VARIANCE_OPTION_ID
import com.jetbrains.python.psi.LanguageLevel

class PyTypeInlayHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {

  fun `test reveal type`() {
    doTest("""
    from typing import reveal_type
    
    def foo(a: int) -> str:
        reveal_type(a)/*<# int #>*/
        return "Hi!"
    
    reveal_type(foo(1))/*<# str #>*/
    """, REVEAL_TYPE_OPTION_ID)
  }

  fun `test function return type`() {
    doTest("""
    def foo(a: int) -> str: # no inlay here
        return "Hi!"
    
    def bar(a: int)/*<# -> Literal["Hi!"] #>*/:
        return "Hi!"
        
    def gen(a: int)/*<# -> Generator[Literal[42, "str"] | float, Any, Literal["Hi!", 42… #>*/:
        yield 42
        yield "str"
        yield 42.5
        if a > 0:
            return "Hi!"
        return 42
    """, FUNCTION_RETURN_TYPE_OPTION_ID)
  }

  fun `test preview`() {
    doTest("""    
    from typing import reveal_type
    
    def example(x: int, y: float)/*<# -> float | int #>*/:
        reveal_type(x + y)/*<# float | int #>*/
        return x + y
    
    reveal_type(example(1, 2.5))/*<# float | int #>*/
    """)
  }

  fun `test variance on type variable`() {
    doTest("""
    from typing import TypeVar, Generic
    
    T = TypeVar("T", infer_variance=True)
    
    class A(Generic[/*<# out #>*/T]):
        def method(self) -> T:
            pass
    """, VARIANCE_OPTION_ID)
  }

  fun `test variance on two type variables`() {
    doTest("""
    from typing import TypeVar, Generic
    
    T1 = TypeVar("T1", infer_variance=True)
    T2 = TypeVar("T2", infer_variance=True)
    
    class A(Generic[/*<# out #>*/T1, /*<# in #>*/T2]):
        def method(self) -> T1: pass
        def method(self, t2: T2): pass
    """, VARIANCE_OPTION_ID)
  }

  fun `test variance on type variable disabled when invariant`() {
    doTest("""
    from typing import TypeVar, Generic
    
    T = TypeVar("T", infer_variance=True)
    
    class A(Generic[T]): # no hint here
        def method(self, t: T) -> T:
          pass
    """, false, VARIANCE_OPTION_ID)
  }

  fun `test variance on type variable when explicitly using co or contra variance`() {
    doTest("""
    from typing import TypeVar, Generic
    
    T_co = TypeVar("T_co", covariant=True)
    T_contra = TypeVar("T_contra", contravariant=True)
    
    class A(Generic[/*<# out #>*/T_co]):
        def method(self) -> T_co:
          pass
    
    class A(Generic[/*<# in #>*/T_contra]):
        def method(self, t: T_contra):
          pass
    """, VARIANCE_OPTION_ID)
  }

  fun `test variance on type parameter`() {
    doTest("""
    class A[/*<# in #>*/T]:
        def method(self, t: T):
          pass
    """, VARIANCE_OPTION_ID)
  }

  fun `test variance on two type parameters`() {
    doTest("""
    class A[/*<# in #>*/T1, /*<# out #>*/T2]:
        def method(self, t1: T1): pass
        def method(self) -> T2: pass
    """, VARIANCE_OPTION_ID)
  }

  fun `test variance on all parameter kinds`() {
    doTest("""
      from typing import Callable
      
      class A[/*<# in #>*/T, /*<# out #>*/*Ts, in **P]:
          def method1(self, t: T): pass
          def method2(self) -> tuple[*Ts]: pass
          def method3(self) -> Callable[P, None]: pass
    """, VARIANCE_OPTION_ID)
  }

  fun `test variance on type parameter disabled when invariant`() {
    doTest("""
    class A[T]: # no hint here
        def method(self, t: T) -> T:
          pass
    """, false, VARIANCE_OPTION_ID)
  }

  fun `test variance on type parameter of type alias`() {
    doTest("""
    type MyTuple[/*<# out #>*/T] = tuple[T]
    """, false, VARIANCE_OPTION_ID)
  }

  @TestFor(issues = ["PY-82956"])
  fun `test async def`() {
    doTest("""
      async def foo()/*<# -> Literal[1] #>*/:
          return 1
    """.trimIndent())
  }

  fun `test parameter type inlay`() {
    doTest("""
      def f(a/*<# : int #>*/):
          '''
          :type a: int
          '''
    """, PARAMETER_TYPE_ANNOTATION)
  }

  fun `test parameter type inlay with default`() {
    doTest("""
      def f(a/*<# : int #>*/=1):
          pass
    """, PARAMETER_TYPE_ANNOTATION)
  }

  fun `test parameter type hint with annotation`() {
    doTest("""
      def f(a: int): # no hint when already annotated
          pass
    """, false, PARAMETER_TYPE_ANNOTATION)
  }

  @TestFor(issues = ["PY-87813"])
  fun `test parameter type hint skips self cls`() {
    doTest("""
      class A:
          def __new__(cls, a/*<# : int #>*/=1):
              cls[0]
          
          def f(self, a/*<# : int #>*/=1):
              self[0]
          
          @classmethod
          def c(cls, a/*<# : int #>*/=1):
              cls[0]

        
      def f(self/*<# : {__getitem__} #>*/, a/*<# : int #>*/=1):
          self[0]
    """, PARAMETER_TYPE_ANNOTATION)
  }

  fun `test parameter type hint variadic`() {
    doTest("""
      def f(*args/*<# : int #>*/, **kwargs/*<# : str #>*/):
          '''
          :type args: int
          :type kwargs: str
          '''
    """, PARAMETER_TYPE_ANNOTATION)
  }

  @TestFor(issues = ["PY-90411"])
  fun `test solved type parameters of generic class`() {
    doTest("""
      class A[T]:
          def __init__(self, t: T):
              self.t = t

      A/*<# [int] #>*/(1)
    """, SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID)
  }

  @TestFor(issues = ["PY-90411"])
  fun `test solved type parameters of generic function`() {
    doTest("""
      def f[T](t: T) -> T: ...

      f/*<# [int] #>*/(1)
    """, SOLVED_FUNCTION_TYPE_PARAMETERS_OPTION_ID)
  }

  @TestFor(issues = ["PY-90411"])
  fun `test solved type parameters with multiple type parameters`() {
    doTest("""
      def f[K, V](k: K, v: V) -> None: ...

      f/*<# [str, int] #>*/("a", 1)
    """, SOLVED_FUNCTION_TYPE_PARAMETERS_OPTION_ID)
  }

  @TestFor(issues = ["PY-90411"])
  fun `test solved type parameters with param spec`() {
    doTest("""
      from typing import Callable

      def f(*, a: int) -> int: ...

      class A[**P]:
          def __init__(self, fn: Callable[P, object]): ...

      A/*<# [[*, a: int]] #>*/(fn=f)
    """, SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID)
  }

  @TestFor(issues = ["PY-90411"])
  fun `test solved type parameters with type var tuple`() {
    doTest("""
      class A[*Ts]:
          def __init__(self, *args: *Ts): ...

      A/*<# [int, str] #>*/(1, "a")
    """, SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID)
  }

  @TestFor(issues = ["PY-90411"])
  fun `test solved type parameters from enclosing scope type parameter`() {
    doTest("""
      class Box[T]:
          def __init__(self, value: T): ...

      def f[T](x: T, y: int):
          Box/*<# [T] #>*/(x)
          Box/*<# [int] #>*/(y)
    """, SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID)
  }

  @TestFor(issues = ["PY-90411"])
  fun `test generic function type argument after constraint solving`() {
    doTest("""
      def select[T](x: T, y: T):
          return x

      select/*<# [str | int] #>*/("foo", 42)
    """, SOLVED_FUNCTION_TYPE_PARAMETERS_OPTION_ID)
  }

  @TestFor(issues = ["PY-90411"])
  fun `test no solved type parameters hint for non generic call`() {
    doTest("""
      def f(t: int) -> int: ...

      class A:
          pass

      f(1)
      A()
    """, false, SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID, SOLVED_FUNCTION_TYPE_PARAMETERS_OPTION_ID)
  }

  @TestFor(issues = ["PY-90411"])
  fun `test solved type parameters hint when new returns unrelated type`() {
    doTest("""
      class A[T]:
          def __new__(cls, t: T) -> list[T]: ...

      A/*<# [int] #>*/(1)
    """, false, SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID)
  }

  @TestFor(issues = ["PY-90411"])
  fun `test no solved type parameters hint when explicitly parameterized`() {
    doTest("""
      class A[T]:
          def __init__(self, t: T):
              self.t = t

      A[int](1)
    """, false, SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID)
  }

  private val allOptions = mapOf(
    REVEAL_TYPE_OPTION_ID to true,
    FUNCTION_RETURN_TYPE_OPTION_ID to true,
    VARIANCE_OPTION_ID to true,
    PARAMETER_TYPE_ANNOTATION to true,
    SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID to true,
    SOLVED_FUNCTION_TYPE_PARAMETERS_OPTION_ID to true,
  )

  private fun doTest(text: String, vararg enabledOptions: String) {
    doTest(text, true, *enabledOptions)
  }

  private fun doTest(text: String, verifyHintsPresence: Boolean, vararg enabledOptions: String) {
    val testOptions = if (enabledOptions.isEmpty()) allOptions else allOptions.mapValues { (key, _) -> key in enabledOptions }
    doTestProvider("A.py",
                   text.trimIndent(),
                   PyTypeInlayHintsProvider(),
                   testOptions,
                   verifyHintsPresence = verifyHintsPresence,
                   testMode = ProviderTestMode.SIMPLE)
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return PyLightProjectDescriptor(LanguageLevel.getLatest())
  }
}