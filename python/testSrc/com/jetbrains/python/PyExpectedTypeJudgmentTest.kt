// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.StackOverflowPreventedException
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.PyExpectedTypeJudgement.getExpectedType
import com.jetbrains.python.psi.types.TypeEvalContext
import junit.framework.ComparisonFailure
import junit.framework.TestCase


class PyExpectedTypeJudgmentTest : PyTestCase() {

  private fun doTest(expression: String, expectedType: String, text: String) {
    doTest(expression, PyExpression::class.java, expectedType, text)
  }

  private fun doTest(expression: String, clazz: Class<out PyExpression>, expectedType: String, text: String) {
    val textIndented = text.trimIndent()
    myFixture.configureByText(PythonFileType.INSTANCE, textIndented)
    val expr: PyExpression = myFixture.findElementByText(expression, clazz)

    RecursionManager.assertOnRecursionPrevention(myFixture.projectDisposable)
    val context = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile())
    val actual = getExpectedType(expr, context)
    val actualType = PythonDocumentationProvider.getTypeName(actual, context)
    TestCase.assertEquals(expectedType, actualType)
  }


  fun testParenthesisExpression() {
    doTest("1", "int", """
      x : int = (1)
      """)
  }

  fun testWalrusExpression() {
    doTest("34", "int", """
      a : int
      a = (b := 34)
      """)
  }

  fun testWalrusInTupleExpression() {
    doTest("34", "int", """
      x: int
      x, y = ((y := 34), 5)
      """)
  }

  fun testWalrusInParentheses() {
    doTest("34", "object", """
      b: object
      (b := 34)
      """)
  }

  fun testReassignFunctionParameter() {
    doTest("expr", "int", """
      def f(b: int) :
        b = expr
      """)
  }

  fun testExpressionAssignedToSlice() {
    doTest("expr", "Iterable[int]", """
      a: list[int]
      a[:] = expr
      """)
  }

  fun testNestedExpressionAssignedToSlice() {
    doTest("expr", "int", """
      a: list[int]
      a[:] = (expr,)
      """)
  }

  fun testStartIndexInSlice() {
    doTest("start", "int | None", """
      a: list[int]
      a[start:] = [1]
      """)
  }

  fun testStopIndexInSlice() {
    doTest("stop", "int | None", """
      a: list[int]
      a[:stop] = [1]
      """)
  }

  fun testStepIndexInSlice() {
    doTest("step", "int | None", """
      a: list[int]
      a[::step] = [1]
      """)
  }

  fun testTupleAsArgument() {
    doTest("(expr, \"spam\")", "Iterable[str]", """
      from typing import Iterable
      
      def f(xs: Iterable[str]):
          ...
      f((expr, "spam"))
      """)
  }

  fun testExpressionInsideTupleAsArgument() {
    doTest("expr", "str", """
      from typing import Iterable
      
      def f(xs: Iterable[str]):
          ...
      f((expr, "spam"))
      """)
  }

  fun testExpressionInsideLambdaAsArgument1() {
    doTest("expr", "int", """
      from typing import Callable
      
      def f(fn: Callable[[int], object]):
          ...
      f(lambda expr: {})
      """)
  }

  fun testExpressionInsideLambdaAsArgument2() {
    doTest("expr", "str", """
      from typing import Callable
      
      def f(fn: Callable[[int], str]):
          ...
      f(lambda x: expr)
      """)
  }

  fun testExpressionInsideLambdaAsUntypedArgument() {
    doTest("expr", "Any", """
      def f(fn):
          ...
      f(lambda expr: 2)
      """)
  }

  fun testExpressionInsideLambdaBodyAsUntypedArgument() {
    doTest("\"hello\"", "Any", """
      def f(fn):
          ...
      f(lambda x = 2: (x := "hello"))
      """)
  }

  fun testExpressionInsideLambdaBodyAsAnyTypedArgument() {
    doTest("\"hello\"", "Any", """
      from typing import Callable
      
      def f(fn: Callable[[Any], Any]):
          ...
      f(lambda x = 2: (x := "hello"))
      """)
  }

  fun testExpressionInsideLambdaBodyAsIntTypedArgument() {
    doTest("\"hello\"", "int", """
      from typing import Callable
      
      def f(fn: Callable[[int], Any]):
          ...
      f(lambda x: (x := "hello"))
      """)
  }

  fun testExpressionInsideLambdaBodyAsIntTypedReturn() {
    doTest("\"hello\"", "int", """
      from typing import Callable
      
      def f(fn: Callable[[Any], int]):
          ...
      f(lambda x: (x := "hello"))
      """)
  }

  fun testExpressionAsReturnValue() {
    doTest("expr", "str", """
      from typing import Iterable
      
      def f(xs) -> str:
          return expr
      """)
  }

  fun testExpressionInCallTargetAsReturnValue() {
    doTest("expr()", PyCallExpression::class.java, "int", """
      def main() -> int:
          return expr()
      """)
  }

  fun testTupleAsReturnValue() {
    doTest("(expr, \"spam\")", "Iterable[str]", """
      from typing import Iterable
      
      def f(xs) -> Iterable[str]:
          return (expr, "spam")
      """)
  }

  fun testExpressionInsideTupleAsReturnValue() {
    doTest("expr", "str", """
      from typing import Iterable
      
      def f(xs) -> Iterable[str]:
          return (expr, "spam")
      """)
  }

  fun testExpressionInsideLambdaAsReturnValue1() {
    doTest("expr", "int", """
      from typing import Callable
      
      def f() -> Callable[[int], str]:
        return lambda expr: "r"
      """)
  }

  fun testExpressionInsideLambdaAsReturnValue2() {
    doTest("expr", "str", """
      from typing import Callable
      
      def f() -> Callable[[int], str]:
        return lambda x: expr
      """)
  }

  fun testExpressionInAssignmentToAttribute() {
    doTest("expr", "int", """
      class A:
          a : int = 1
          
      A.a = expr
      """)
  }

  fun testExpressionInsideLambdaOfGenericFunction() {
    fixme("PY-85922", StackOverflowPreventedException::class.java) {
      doTest("expr", "int", """
      from collections.abc import Callable, Iterable
      
      def f[T](x: Iterable[T], y: Callable[[T], object]): ...
      
      f([1], lambda expr: ...)
    """)
    }
  }

  fun testExpressionInsideGenericClassAsReturnValue1() {
    fixme("PY-85922", StackOverflowPreventedException::class.java) {
      doTest("expr", "int", """
        from typing import Callable
        
        class A[T]:
            def f(self, fn: Callable[[T], str]) -> float:
        
        A[int]().f(lambda expr: "s")
        """)
    }
  }

  fun testExpressionInsideGenericClassAsReturnValue2() {
    fixme("PY-85922", StackOverflowPreventedException::class.java) {
      doTest("expr", "int", """
        from typing import Callable
        
        class A[T]:
            def f(self, fn: Callable[[str], T]) -> float:
        
        A[int]().f(lambda x: expr)
        """)
    }
  }

  fun testTupleAsReturnValueNoTypeHint() {
    doTest("(expr, \"spam\")", "Any", """
      def f(xs):
          return (expr, "spam")
      """)
  }

  fun testExpressionInsideTupleAsReturnValueNoTypeHint() {
    doTest("expr", "Any", """
      def f(xs):
          return (expr, "spam")
      """)
  }

  fun testTupleAsAssignmentValue() {
    doTest("(42, (expr, \"spam\"))", "tuple[Any, tuple[str, Any]]", """
      x2: str
      x1, (x2, x3) = (42, (expr, "spam"))
      """)
  }

  fun testExpressionInsideTupleAsAssignmentValue() {
    doTest("expr", "str", """
      x2: str
      x1, (x2, x3) = (42, (expr, "spam"))
      """)
  }

  fun testTupleAsAssignmentValueNoTypeHint() {
    doTest("(42, (expr, \"spam\"))", "tuple[Any, tuple[Any, Any]]", """
      x1, (x2, x3) = (42, (expr, "spam"))
      """)
  }

  fun testExprAsAssignmentValueNoTypeHint() {
    doTest("expr", "Iterable[Any]", """
      x1, (x2, x3) = expr
      """)
  }

  fun testExpressionInsideTupleAsAssignmentValueNoTypeHint() {
    doTest("expr", "Any", """
      x1, (x2, x3) = (42, (expr, "spam"))
      """)
  }

  fun testExpressionAsAssignmentValueToList() {
    doTest("expr", "int", """
      x: list[int]
      x[0] = expr
      """)
  }

  fun testExpressionInsideTupleAsAssignmentValueToList() {
    doTest("expr", "str", """
      x1: bool
      x2: str
      x3: int
      x1, [x2, x3] = (true, (expr, "spam"))
      """)
  }

  fun testExpressionInsideTupleAsAssignmentValueToListNoTypeHint() {
    doTest("expr", "Any", """
      x1, [x2, x3] = (42, (expr, "spam"))
      """)
  }

  fun testExpressionAsAssignmentValueToUnwrap1() {
    doTest("expr", "Iterable[int]", """
      x: int
      xs: tuple[int, ...]
      x, *xs = expr
      """)
  }

  fun testExpressionAsTupleElementToUnwrap1() {
    doTest("expr", "int", """
      x: int
      xs: tuple[int, ...]
      x, *xs = 1, 2, expr
      """)
  }

  fun testExpressionAsAssignmentValueToUnwrap2() {
    doTest("expr", "Iterable[int | str]", """
      x: int
      xs: tuple[int, str]
      x, *xs = expr
      """)
  }

  fun testExpressionAsTupleElementToUnwrap2() {
    doTest("expr", "str", """
      x: int
      xs: tuple[int, str]
      x, *xs = 1, 2, expr
      """)
  }

  fun testExpressionAsTupleElementToUnwrap2OutOfBounds() {
    doTest("expr", "Any", """
      x: int
      xs: tuple[int, str]
      x, *xs = 1, 2, "3", expr
      """)
  }

  fun testExpressionInVariadicTupleEnd1() {
    doTest("expr", "str", """
      x: tuple[str, *tuple[int, ...]] = expr, 2, 3
      """)
  }

  fun testExpressionInVariadicTupleEnd2() {
    doTest("expr", "int", """
      x: tuple[str, *tuple[int, ...]] = "s", expr, 3
      """)
  }

  fun testExpressionInVariadicTupleEnd3() {
    doTest("expr", "int", """
      x: tuple[str, *tuple[int, ...]] = "s", 2, expr
      """)
  }

  fun testExpressionInVariadicTupleMiddle1() {
    doTest("expr", "str", """
      x: tuple[str, *tuple[int, ...], float] = expr, 2, 3.14
      """)
  }

  fun testExpressionInVariadicTupleMiddle2() {
    doTest("expr", "int", """
      x: tuple[str, *tuple[int, ...], float] = "s", expr, 3.14
      """)
  }

  fun testExpressionInVariadicTupleMiddle3() {
    doTest("expr", "float", """
      x: tuple[str, *tuple[int, ...], float] = "s", 2, expr
      """)
  }

  fun testExpressionInVariadicTupleStart1() {
    doTest("expr", "int", """
      x: tuple[*tuple[int, ...], str] = expr, 2, "s"
      """)
  }

  fun testExpressionInVariadicTupleStart2() {
    doTest("expr", "int", """
      x: tuple[*tuple[int, ...], str] = 1, expr, "s"
      """)
  }

  fun testExpressionInVariadicTupleStart3() {
    doTest("expr", "str", """
      x: tuple[*tuple[int, ...], str] = 1, 2, expr
      """)
  }

  fun testSubscriptionExpression() {
    doTest("expr", "Literal[\"1\", 2, \"foo\"]", """
      from typing import Literal
      
      d: dict[Literal["1", 2, "foo"], str] = {}
      d[expr]
      """)
  }

  fun testArgumentForArgs() {
    doTest("expr", "str", """
      def f(*args: str):
          pass

      f(expr)
      """)
  }

  fun testArgumentForArgsOfUnpackedTuple1() {
    doTest("expr", "int", """
      def f(*args: *tuple[int]):
          pass

      f(expr)
      """)
  }

  fun testArgumentForArgsOfUnpackedTuple2() {
    doTest("expr", "Any", """
      def f(*args: *tuple[int]):
          pass

      f(1, expr)
      """)
  }

  fun testArgumentForArgsOfUnpackedTuple3() {
    doTest("expr", "str", """
      def f(*args: *tuple[int,str]):
          pass

      f(1, expr)
      """)
  }

  fun testArgumentForArgsOfUnpackedTuple4() {
    doTest("expr", "int", """
      def f(*args: *tuple[int,...]):
          pass

      f(1, expr)
      """)
  }

  fun testArgumentValueForKwArgs() {
    doTest("value", "str", """
      def f(**kwargs: str):
          pass

      f(foo="value")
      """)
  }

  fun testArgumentKeyForKwArgs() {
    doTest("foo", "str", """
      def f(**kwargs: str):
          pass
      
      f(foo="value")
      """)
  }

  fun testArgumentForPlainParameter() {
    doTest("expr", "str", """
      def f(x: int, y: str):
          pass

      f(42, expr)
      """)
  }

  fun testValueForTrivialAssignment() {
    doTest("expr", "str", """
      x: str = expr
      """)
  }

  fun testLambdaInAssignment() {
    doTest("lambda p_x, p_y: p_x + p_y", "(str, int) -> int", """
      from typing import Callable
      
      adder: Callable[[str, int], int] = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testParameter1OfLambdaInAssignment() {
    doTest("p_x", "str", """
      from typing import Callable
      
      adder: Callable[[str, int], int] = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testParameter2OfLambdaInAssignment() {
    doTest("p_y", "int", """
      from typing import Callable
      
      adder: Callable[[str, int], int] = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testReturnOfLambdaInAssignment() {
    doTest("p_x + p_y", PyBinaryExpression::class.java, "int", """
      from typing import Callable
      
      adder: Callable[[str, int], int] = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testNestedLambda() {
    doTest("yy", "float", """
      from typing import Callable
      
      func: Callable[[int], Callable[[float], str]] = lambda xx: lambda yy: "Hi"
      """)
  }

  fun testLambdaInAssignment_PreviouslyTyped() {
    doTest("lambda p_x, p_y: p_x + p_y", "(str, int) -> int", """
      from typing import Callable
      
      adder: Callable[[str, int], int]
      adder = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testParameter1OfLambdaInAssignment_PreviouslyTyped() {
    doTest("p_x", "str", """
      from typing import Callable
      
      adder: Callable[[str, int], int]
      adder = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testParameter2OfLambdaInAssignment_PreviouslyTyped() {
    doTest("p_y", "int", """
      from typing import Callable
      
      adder: Callable[[str, int], int]
      adder = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testReturnOfLambdaInAssignment_PreviouslyTyped() {
    doTest("p_x + p_y", PyBinaryExpression::class.java, "int", """
      from typing import Callable
      
      adder: Callable[[str, int], int]
      adder = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testNestedLambda_PreviouslyTyped() {
    doTest("yy", "float", """
      from typing import Callable
      
      func: Callable[[int], Callable[[float], str]]
      func = lambda xx: lambda yy: "Hi"
      """)
  }

  fun testLambdaInAssignment_TypedAsAttribute() {
    doTest("lambda p_x, p_y: p_x + p_y", "(str, int) -> int", """
      from typing import Callable
      
      class C:
          attr: Callable[[str, int], int]
          def __init__(self):
              self.attr = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testParameter1OfLambdaInAssignment_TypedAsAttribute() {
    doTest("p_x", "str", """
      from typing import Callable
      
      class C:
          attr: Callable[[str, int], int]
          def __init__(self):
              self.attr = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testParameter2OfLambdaInAssignment_TypedAsAttribute() {
    doTest("p_y", "int", """
      from typing import Callable
      
      class C:
          attr: Callable[[str, int], int]
          def __init__(self):
              self.attr = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testReturnOfLambdaInAssignment_TypedAsAttribute() {
    doTest("p_x + p_y", PyBinaryExpression::class.java, "int", """
      from typing import Callable
      
      class C:
          attr: Callable[[str, int], int]
          def __init__(self):
              self.attr = lambda p_x, p_y: p_x + p_y
      """)
  }

  fun testNestedLambda_TypedAsAttribute() {
    doTest("yy", "float", """
      from typing import Callable
      
      class C:
          attr: Callable[[int], Callable[[float], str]]
          def __init__(self):
              self.attr = lambda xx: lambda yy: "Hi"
      """)
  }

  fun testListLiteral() {
    doTest("[expr, 2, 3]", "list[int]", """
      from typing import List
      
      v: List[int] = [expr, 2, 3]
      """)
  }

  fun testExpressionInList() {
    doTest("expr", "int", """
      from typing import List
      
      v: List[int] = [expr, 2, 3]
      """)
  }

  fun testStarArgumentExpressionInList() {
    doTest("expr", "Iterable[int]", """
      from typing import List
      
      v: List[int] = [1, *expr, 4]
      """)
  }

  fun testExpressionInStarArgumentExpressionInList() {
    doTest("expr", "int", """
      from typing import List
      
      v: List[int] = [1, *[expr, 3], 4]
      """)
  }

  fun testDictLiteralAsArgument() {
    doTest("{'key': expr}", "dict[str, int]", """
      v: dict[str, int] = {'key': expr}
      """)
  }

  fun testDictKeyInDictLiteral() {
    doTest("key", "str", """
      v: dict[str, int] = {'key': expr}
      """)
  }

  fun testDictValueInDictLiteral() {
    doTest("value", "int", """
      v: dict[str, int] = {'key': value}
      """)
  }

  fun testStarArgumentInDictLiteral() {
    doTest("expr", "Mapping[str, int]", """
      v: dict[str, int] = {'key': 1, **expr}
      """)
  }

  fun testDoubleStarExpressionOwnTypeShouldBeAny() {
    doTest("**xs", "Any", """
      ys: dict[str, int] = {**xs}
      """)
  }

  fun testKeyInStarArgumentInDictLiteral() {
    doTest("expr", "str", """
      v: dict[str, int] = {'key': 1, **{expr: 2}}
      """)
  }

  fun testValueInStarArgumentInDictLiteral() {
    doTest("expr", "int", """
      v: dict[str, int] = {'key': 1, **{"otherKey": expr}}
      """)
  }

  fun testSetLiteralAsArgument() {
    doTest("{expr, 2, 3}", "set[int]", """
      from typing import Set
      
      v: Set[int] = {expr, 2, 3}
      """)
  }

  fun testExpressionInsideSetAsArgument() {
    doTest("expr", "int", """
      from typing import Set
      
      v: Set[int] = {expr, 2, 3}
      """)
  }

  fun testNonStarredExpressionAsArgument() {
    doTest("expr", "int", """
      def f(*args: int):
          pass
      
      f(expr)
      """)
  }

  fun testStarredExpressionAsArgument() {
    doTest("expr", "tuple[int, ...]", """
      def f(*args: int):
          pass
      
      f(*expr)
      """)
  }

  fun testStarredExpressionElementAsArgument1() {
    doTest("123", "int", """
      def f(*args: int):
          pass
      
      f(*(123, 456))
      """)
  }

  fun testStarredExpressionElementAsArgument2() {
    doTest("123", "int", """
      def f(s: str, *args: int):
          pass
      
      f("foo", *(123, 456))
      """)
  }

  fun testStarredExpressionElementAsArgument3() {
    doTest("123", "int", """
      def f(s: str, n: int):
          pass
      
      f(*("foo", 123))
      """)
  }

  fun testDoubleStarredExpressionElementAsArgument1() {
    doTest("123", "int", """
      def f(**kwargs: int):
          pass
      
      f(**{"s": 123, "n": 456})
      """)
  }

  fun testDoubleStarredExpressionElementAsArgument2() {
    doTest("123", "int", """
      def f(s: str, **kwargs: int):
          pass
      
      f("foo", **{"s2": 123, "n": 456})
      """)
  }

  fun testDoubleStarredExpressionElementAsArgument3() {
    doTest("123", "int", """
      def f(s: str, n: int):
          pass
      
      f(**{"s": "foo", "n": 123})
      """)
  }

  fun testDoubleStarredExpressionElementAsArgument1B() {
    doTest("123", "int", """
      from typing import TypedDict, Unpack
      
      class FArgs(TypedDict):
          s: str
          n: int
    
      def f(**kwargs: Unpack[FArgs]):
          pass
      
      f(**{"s": "foo", "n": 123})
      """)
  }

  fun testDoubleStarredExpressionElementAsArgument2B() {
    doTest("123", "int", """
      from typing import TypedDict, Unpack
      
      class FArgs(TypedDict):
          s: str
          n: int
    
      def f(s: str, **kwargs: Unpack[FArgs]):
          pass
      
      f("foo", **{"s": "foo", "n": 123})
      """)
  }

  fun testDoubleStarredExpressionElementAsArgumentCombiningUnpackedTypedDictAndOtherParameterTypes() {
    doTest("expr", "str", """
      from typing import TypedDict, Unpack
      
      class FArgs(TypedDict):
          s: str
          n: int
    
      def f(a: str, **kwargs: Unpack[FArgs]):
          pass
      
      f(**{"s": "foo", "n": 123, "a": expr})
      """)
  }

  fun testGenericMethodArgument() {
    doTest("expr", "str", """
      class Box[T]:
          def m(self, x: T):
              ...
      b: Box[str]
      b.m(expr)
      """)
  }

  fun testGenericFunctionArgument() {
    doTest("expr", "int", """
      def f[T](x: T, y: T)
          ...
      
      f(42, expr)
      """)
  }

  fun testStarExpressionOwnTypeShouldBeInt() {
    doTest("*xs", "int", """
      ys: list[int] = [1, *xs]
      """)
  }

  fun testStarExpressionInSetLiteral() {
    doTest("xs", "Iterable[int]", """
      ys: set[int] = {1, *xs}
      """)
  }

  fun testStarExpressionInTupleLiteral() {
    doTest("xs", "Iterable[int]", """
      ys: tuple[int, ...] = (1, *xs)
      """)
  }

  fun testExpressionInTupleLiteral() {
    doTest("x", "int", """
      ys: tuple[int, ...] = [1, x, 3]
      """)
  }

  fun testExprNonDoubleStarredExpressionAsArgument() {
    doTest("expr", "int", """
      def f(**kwargs: int):
          pass
      
      f(param = expr)
      """)
  }

  fun testParamNonDoubleStarredExpressionAsArgument() {
    doTest("param", "str", """
      def f(**kwargs: str):
          pass
      
      f(param = expr)
      """)
  }

  fun testDoubleStarredExpressionAsArgument() {
    doTest("**expr", "dict[str, str]", """
      def f(**kwargs: str):
          pass
      
      f(**expr)
      """)
  }

  fun testDoubleStarredExpressionKeyAsArgument() {
    doTest("key", "str", """
      def f(**kwargs: str):
          pass
      
      f(**{"key" : 0})
      """)
  }

  fun testDoubleStarredExpressionValueAsArgument() {
    doTest("0", "str", """
      def f(**kwargs: str):
          pass
      
      f(**{"key" : 0})
      """)
  }

  fun testArgumentOfOverloadedFunctions() {
    doTest("expr", "int | str", """
      from typing import overload
      
      @overload
      def f(x: int) -> int: ...
      
      @overload
      def f(x: str) -> str: ...
      
      def f(x): return x
      
      f(expr)
      """)
  }

  fun testArgumentOfOverloadedFunctionsBoundedByReturn() {
    fixme("Depends on correct function overload matching", ComparisonFailure::class.java) {
      doTest("expr", "str", """
      from typing import overload
      
      @overload
      def f(x: int) -> int: ...
      
      @overload
      def f(x: str) -> str: ...
      
      def f(x): return x
      
      a: str = f(expr)
      """)
    }
  }

  fun testReturnOfOverloadedFunctions() {
    fixme("Depends on correct function overload matching", ComparisonFailure::class.java) {
      doTest("expr", "int", """
      from typing import overload
      
      @overload
      def f(x: int) -> int: ...
      
      @overload
      def f(x: str) -> str: ...
      
      def f(x): return x
      
      expr = f(1)
      """)
    }
  }

  fun testReturnInAsyncFunction() {
    doTest("expr", "object", """
      async def foo() -> object:
          return expr
      """)
  }

  fun testYieldExpressionInTypedGenerator() {
    doTest("send", "int", """
      from typing import Generator
      
      def f() -> Generator[int, str, float]:
          receive = yield send
          return result
      """)
  }

  fun testReturnInTypedGenerator() {
    doTest("result", "float", """
      from typing import Generator
      
      def f() -> Generator[int, str, float]:
          receive = yield send
          return result
      """)
  }

  fun testYieldExpressionFromGenerator() {
    doTest("expr", "Iterable[int]", """
      from typing import Generator
      
      def main() -> Generator[int]:
          yield from expr
      """)
  }

  // Note: The return type of yield is not subject to the [PyExpectedTypeJudgement] computation.
  @Suppress("unused")
  fun do_not_testReturnFromYieldExpressionInTypedGenerator() {
    doTest("receive", "Any", """
      from typing import Generator
      
      def f() -> Generator[int, str, float]:
          receive = yield send
          return result
      """)
  }
}
