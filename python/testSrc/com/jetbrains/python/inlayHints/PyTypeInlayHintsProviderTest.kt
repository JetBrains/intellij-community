// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.inlayHints.PyTypeInlayHintsProvider.Companion.FUNCTION_RETURN_TYPE_OPTION_ID
import com.jetbrains.python.inlayHints.PyTypeInlayHintsProvider.Companion.PARAMETER_TYPE_ANNOTATION
import com.jetbrains.python.inlayHints.PyTypeInlayHintsProvider.Companion.REVEAL_TYPE_OPTION_ID
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
    
    def bar(a: int)/*<# -> str #>*/:
        return "Hi!"
        
    def gen(a: int)/*<# -> Generator[int | str | float, Any, str | int] #>*/:
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
    
    def example(x: int, y: float)/*<# -> float #>*/:
        reveal_type(x + y)/*<# float #>*/
        return x + y
    
    reveal_type(example(1, 2.5))/*<# float #>*/
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
      async def foo()/*<# -> int #>*/:
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

  private val allOptions = mapOf(
    REVEAL_TYPE_OPTION_ID to true,
    FUNCTION_RETURN_TYPE_OPTION_ID to true,
    VARIANCE_OPTION_ID to true,
    PARAMETER_TYPE_ANNOTATION to true,
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