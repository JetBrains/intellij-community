/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tests for a type system based on mypy's typing module.
 *
 */
public class PyTypingTest extends PyTestCase {

  public void testClassType() {
    doTest("Foo",
           """
             class Foo:    pass

             def f(expr: Foo):
                 pass
             """);
  }

  public void testClassReturnType() {
    doTest("Foo",
           """
             class Foo:    pass

             def f() -> Foo:
                 pass

             expr = f()
             """);
  }

  public void testNoneType() {
    doTest("None",
           """
             def f(expr: None):
                 pass
             """);
  }

  public void testNoneReturnType() {
    doTest("None",
           """
             def f() -> None:
                 return 0
             expr = f()
             """);
  }

  public void testUnionType() {
    doTest("int | str",
           """
             from typing import Union

             def f(expr: Union[int, str]):
                 pass
             """);
  }

  public void testBuiltinList() {
    doTest("list",
           """
             from typing import List

             def f(expr: List):
                 pass
             """);
  }

  public void testBuiltinListWithParameter() {
    doTest("list[int]",
           """
             from typing import List

             def f(expr: List[int]):
                 pass
             """);
  }

  public void testBuiltinDictWithParameters() {
    doTest("dict[str, int]",
           """
             from typing import Dict

             def f(expr: Dict[str, int]):
                 pass
             """);
  }

  public void testBuiltinTuple() {
    doTest("tuple",
           """
             from typing import Tuple

             def f(expr: Tuple):
                 pass
             """);
  }

  public void testBuiltinTupleWithParameters() {
    doTest("tuple[int, str]",
           """
             from typing import Tuple

             def f(expr: Tuple[int, str]):
                 pass
             """);
  }

  public void testAnyType() {
    doTest("Any",
           """
             from typing import Any

             def f(expr: Any):
                 pass
             """);
  }

  public void testGenericType() {
    doTest("A",
           """
             from typing import TypeVar

             T = TypeVar('A')

             def f(expr: T):
                 pass
             """);
  }

  public void testGenericBoundedType() {
    doTest("T",
           """
             from typing import TypeVar

             T = TypeVar('T', int, str)

             def f(expr: T):
                 pass
             """);
  }

  public void testParameterizedClass() {
    doTest("C[int]",
           """
             from typing import Generic, TypeVar

             T = TypeVar('T')

             class C(Generic[T]):
                 def __init__(self, x: T):
                     pass

             expr = C(10)
             """);
  }

  public void testParameterizedClassWithConstructorNone() {
    doTest("C[int]",
           """
             from typing import Generic, TypeVar

             T = TypeVar('T')

             class C(Generic[T]):
                 def __init__(self, x: T) -> None:
                     pass

             expr = C(10)
             """);
  }

  public void testParameterizedClassMethod() {
    doTest("int",
           """
             from typing import Generic, TypeVar

             T = TypeVar('T')

             class C(Generic[T]):
                 def __init__(self, x: T):
                     pass
                 def foo(self) -> T:
                     pass

             expr = C(10).foo()
             """);
  }

  public void testParameterizedClassInheritance() {
    doTest("int",
           """
             from typing import Generic, TypeVar

             T = TypeVar('T')

             class B(Generic[T]):
                 def foo(self) -> T:
                     pass
             class C(B[T]):
                 def __init__(self, x: T):
                     pass

             expr = C(10).foo()
             """);
  }

  public void testAnyStrUnification() {
    doTest("bytes",
           """
             from typing import AnyStr

             def foo(x: AnyStr) -> AnyStr:
                 pass

             expr = foo(b'bar')
             """);
  }

  public void testAnyStrForUnknown() {
    doTest("str | bytes | Any",
           """
             from typing import AnyStr

             def foo(x: AnyStr) -> AnyStr:
                 pass

             def bar(x):
                 expr = foo(x)
             """);
  }

  public void testCallableType() {
    doTest("(int, str) -> str",
           """
             from typing import Callable

             def foo(expr: Callable[[int, str], str]):
                 pass
             """);
  }

  public void testTypeInStringLiteral() {
    doTest("C",
           """
             class C:
                 def foo(self, expr: 'C'):
                     pass
             """);
  }

  public void testQualifiedTypeInStringLiteral() {
    doTest("str",
           """
             import typing

             def foo(x: 'typing.AnyStr') -> typing.AnyStr:
                 pass

             expr = foo('bar')
             """);
  }

  public void testOptionalType() {
    doTest("int | None",
           """
             from typing import Optional

             def foo(expr: Optional[int]):
                 pass
             """);
  }

  // PY-28032
  public void testOptionalOfAny() {
    doTest("Any | None",
           """
             from typing import Optional, Any

             x = None  # type: Optional[Any]
             expr = x
             """);
  }

  public void testOptionalFromDefaultNone() {
    doTest("int | None",
           """
             def foo(expr: int = None):
                 pass
             """);
  }

  public void testFlattenUnions() {
    doTest("int | str | list",
           """
             from typing import Union

             def foo(expr: Union[int, Union[str, list]]):
                 pass
             """);
  }

  public void testCast() {
    doTest("str",
           """
             from typing import cast

             def foo(x):
                 expr = cast(str, x)
             """);
  }

  public void testComment() {
    doTest("int",
           """
             def foo(x):
                 expr = x  # type: int
             """);
  }

  public void testMultiAssignmentComment() {
    doTest("tuple[int, str]",
           """
             def foo(x):
                 c1, c2 = x  # type: int, str
                 expr = c1, c2
             """);
  }

  // PY-19220
  public void testMultiLineAssignmentComment() {
    doTest("list[str]",
           """
             from typing import List

             expr = [
                 a,
                 b,
             ]  # type: List[str]""");
  }

  public void testForLoopComment() {
    doTest("int",
           """
             def foo(xs):
                 for expr, x in xs:  # type: int, str
                     pass
             """);
  }

  public void testWithComment() {
    doTest("int",
           """
             def foo(x):
                 with x as expr:  # type: int
                     pass
             """);
  }

  // PY-21191
  public void testTypeCommentWithParenthesizedTuple() {
    doTest("int",
           "expr, x = undefined()  # type: (int, str) ");
  }

  // PY-21191
  public void testTypeCommentWithNestedTuplesInAssignment() {
    doTest("int",
           "_, (_, expr) = undefined()  # type: str, (str, int)");
  }

  // PY-21191
  public void testTypeCommentStructuralMismatch1() {
    doTest("Any",
           "expr = undefined()  # type: str, int");
  }

  // PY-21191
  public void testTypeCommentStructuralMismatch2() {
    doTest("Any",
           "_, (_, expr) = undefined()  # type: str, (str, str, int)");
  }

  // PY-21191
  public void testTypeCommentStructuralMismatch3() {
    doTest("Any",
           "_, (_, expr) = undefined()  # type: (str, str), int");
  }

  // PY-21191
  public void testTypeCommentWithNestedTuplesInWithStatement() {
    doTest("int",
           "with undefined() as (_, (_, expr)):  # type: str, (str, int)\n" +
           "    pass");
  }

  // PY-21191
  public void testTypeCommentWithNestedTuplesInForStatement() {
    doTest("int",
           "for (_, (_, expr)) in undefined():  # type: str, (str, int)\n" +
           "    pass");
  }

  // PY-16585
  public void testCommentAfterComprehensionInAssignment() {
    doTest("int",
           """
             from typing import List

             xs = [expr for expr in range(10)]  # type: List[int]""");
  }

  // PY-16585
  public void testCommentAfterComprehensionInForLoop() {
    doTest("int",
           "for _ in [str(expr) for expr in range(10)]:  # type: str\n" +
           "    pass");
  }

  // PY-16585
  public void testCommentAfterComprehensionInWithStatement() {
    doTest("int",
           "with f([expr for expr in range(10)]) as _: # type: str\n" +
           "    pass");
  }

  public void testStringLiteralInjection() {
    doTestInjectedText("""
                         class C:
                             def foo(self, expr: '<caret>C'):
                                 pass
                         """,
                       "C");
  }

  public void testStringLiteralInjectionParameterizedType() {
    doTestInjectedText("""
                         from typing import Union, List

                         class C:
                             def foo(self, expr: '<caret>Union[List[C], C]'):
                                 pass
                         """,
                       "Union[List[C], C]");
  }

  // PY-37515
  public void testNoStringLiteralInjectionUnderCall() {
    doTestNoInjectedText("class Model:\n" +
                         "    field: call('<caret>List[str]')");
  }

  // PY-15810
  public void testNoStringLiteralInjectionForNonTypingStrings() {
    doTestNoInjectedText("""
                           class C:
                               def foo(self, expr: '<caret>foo bar'):
                                   pass
                           """);
  }

  // PY-42334
  public void testStringLiteralInjectionForExplicitTypeAlias() {
    doTestInjectedText("""
                         from typing import TypeAlias

                         Alias: TypeAlias = 'any + <caret>text'""",
                       "any + text");
  }

  // PY-42334
  public void testStringLiteralInjectionForExplicitTypeAliasUsingTypeComment() {
    doTestInjectedText("""
                         from typing import TypeAlias

                         Alias = 'any + <caret>text'  # type: TypeAlias""",
                       "any + text");
  }

  // PY-22620
  public void testVariableTypeCommentInjectionTuple() {
    doTestInjectedText("x, y = undefined()  # type: int,<caret> int",
                       "int, int");
  }

  // PY-21195
  public void testVariableTypeCommentWithSubsequentComment() {
    doTestInjectedText("x, y = undefined()  # type: int,<caret> str # comment",
                       "int, str");
  }

  // PY-21195
  public void testVariableTypeCommentWithSubsequentCommentWithoutSpacesInBetween() {
    doTestInjectedText("x, y = undefined()  # type: int,<caret> str# comment",
                       "int, str");
  }

  // PY-22620
  public void testVariableTypeCommentInjectionParenthesisedTuple() {
    doTestInjectedText("x, y = undefined()  # type: (int,<caret> int)",
                       "(int, int)");
  }

  // PY-22620
  public void testForTypeCommentInjectionTuple() {
    doTestInjectedText("for x, y in undefined():  # type: int,<caret> int\n" +
                       "    pass",
                       "int, int");
  }

  // PY-22620
  public void testWithTypeCommentInjectionTuple() {
    doTestInjectedText("with undefined() as (x, y):  # type: int,<caret> int\n" +
                       "    pass",
                       "int, int");
  }

  // PY-16125
  public void testIterableForLoop() {
    doTest("int",
           """
             from typing import Iterable

             def foo() -> Iterable[int]:
                 pass

             for expr in foo():
                 pass
             """);
  }

  // PY-16353
  public void testAssignedType() {
    doTest("Iterable[int]",
           """
             from typing import Iterable

             IntIterable = Iterable[int]

             def foo() -> IntIterable:
                 pass

             expr = foo()
             """);
  }

  // PY-16267
  public void testGenericField() {
    doTest("str",
           """
             from typing import TypeVar, Generic

             T = TypeVar('T', covariant=True)

             class C(Generic[T]):
                 def __init__(self, foo: T):
                     self.foo = foo

             def f() -> C[str]:
                 return C('test')

             x = f()
             expr = x.foo
             """);
    doTest("tuple[Any, Any]", """
      from typing import Any
      
      class A[T]:
          v: T

      def f(a1: A[Any], a2: A):
          expr = a1.v, a2.v
      """);
  }

  // PY-18427 PY-76243
  public void testConditionalTypeAlias() {
    doTest("int",
           """
             if something:
                 Type = int
             else:
                 Type = str

             expr: Type
             """);
  }

  public void testConditionalGenericTypeAliasWithoutExplicitParameter() {
    doTest("list[str]",
           """
             if something:
                 Type = list
             else:
                 Type = set
             
             expr: Type[str]
             """);
  }

  public void testConditionalGenericTypeAliasWithExplicitParameter() {
    doTest("list[str]",
           """
             from typing import TypeVar
             
             T = TypeVar("T")
             
             if something:
                 Type = list[T]
             else:
                 Type = set[T]
             
             expr: Type[str]
             """);
  }

  public void testTypeAliasOfUnionOfGenericTypes() {
    doTest("list[str] | set[str]",
           """
             from typing import TypeVar
             
             T = TypeVar("T")
             
             Type = list[T] | set[T]
             
             expr: Type[str]
             """);
  }

  public void testTypeAliasOfUnionOfGenericTypesWithDifferentArity() {
    doTest("dict[str, int] | set[int]",
           """
             from typing import TypeVar
             
             T1 = TypeVar("T1")
             T2 = TypeVar("T2")
             
             Type = dict[T1, T2] | set[T2]
             
             expr: Type[str, int]
             """);
  }

  // PY-18254
  public void testFunctionTypeComment() {
    doTest("(x: int, args: tuple[float, ...], kwargs: dict[str, str]) -> list[bool]",
           """
             from typing import List

             def f(x, *args, **kwargs):
                 # type: (int, *float, **str) -> List[bool]
                 pass

             expr = f""");
  }

  // PY-18595
  public void testFunctionTypeCommentForStaticMethod() {
    doTest("int",
           """
             class C:
                 @staticmethod
                 def m(some_int, some_bool, some_str):
                     # type: (int, bool, str) -> bool
                     expr = some_int""");
  }

  // PY-18726
  public void testFunctionTypeCommentCallableParameter() {
    doTest("(bool, str) -> int",
           """
             from typing import Callable

             def f(cb):
                 # type: (Callable[[bool, str], int]) -> None
                 expr = cb""");
  }

  // PY-18763  
  public void testCallableTypeWithEllipsis() {
    doTest("(...) -> int",
           """
             from typing import Callable

             expr = unknown() # type: Callable[..., int]""");
  }

  // PY-18763  
  public void testFunctionTypeCommentCallableParameterWithEllipsis() {
    doTest("(...) -> int",
           """
             from typing import Callable

             def f(cb):
                 # type: (Callable[..., int]) -> None
                 expr = cb""");
  }

  // PY-18726
  public void testFunctionTypeCommentBadCallableParameter1() {
    doTest("Any",
           """
             from typing import Callable, Tuple

             def f(cb):
                 # type: (Callable[Tuple[bool, str], int]) -> None
                 expr = cb""");
  }

  // PY-18726
  public void testFunctionTypeCommentBadCallableParameter2() {
    doTest("(bool, int) -> Any",
           """
             from typing import Callable, Tuple

             def f(cb):
                 # type: (Callable[[bool, int], [int]]) -> None
                 expr = cb""");
  }

  // PY-18598
  public void testFunctionTypeCommentEllipsisParameters() {
    doTest("(x: Any, y: Any, z: Any) -> int",
           """
             def f(x, y=42, z='foo'):
                 # type: (...) -> int\s
                 pass

             expr = f""");
  }

  // PY-20421
  public void testFunctionTypeCommentSingleElementTuple() {
    doTest("tuple[int]",
           """
             from typing import Tuple

             def f():
                 # type: () -> Tuple[int]
                 pass

             expr = f()""");
  }

  // PY-18762
  public void testHomogeneousTuple() {
    doTest("tuple[int, ...]",
           """
             from typing import Tuple

             def f(xs: Tuple[int, ...]):
                 expr = xs""");
  }

  // PY-18762
  public void testHomogeneousTupleIterationType() {
    doTest("int",
           """
             from typing import Tuple

             xs = unknown() # type: Tuple[int, ...]

             for x in xs:
                 expr = x""");
  }

  // PY-18762
  public void testHomogeneousTupleUnpackingTarget() {
    doTest("int",
           """
             from typing import Tuple

             xs = unknown() # type: Tuple[int, ...]
             expr, yx = xs""");
  }

  // PY-18762
  public void testHomogeneousTupleMultiplication() {
    doTest("tuple[int, ...]",
           """
             from typing import Tuple

             xs = unknown() # type: Tuple[int, ...]
             expr = xs * 42""");
  }

  // PY-18762
  public void testFunctionTypeCommentHomogeneousTuple() {
    doTest("tuple[int, ...]",
           """
             from typing import Tuple

             def f(xs):
                 # type: (Tuple[int, ...]) -> None
                 expr = xs
             """);
  }

  // PY-18741
  public void testFunctionTypeCommentWithParamTypeComment() {
    doTest("(x: int, y: bool, z: Any) -> str",
           """
             def f(x, # type: int\s
                   y # type: bool
                   ,z):
                 # type: (...) -> str
                 pass

             expr = f""");
  }

  // PY-18877
  public void testFunctionTypeCommentOnTheSameLine() {
    doTest("(x: int, y: int) -> None",
           """
             def f(x,
                   y):  # type: (int, int) -> None
                 pass

             expr = f""");
  }

  // PY-18386
  public void testRecursiveType() {
    doTest("int | Any",
           """
             from typing import Union

             Type = Union[int, 'Type']
             expr = 42 # type: Type""");
  }

  // PY-18386
  public void testRecursiveType2() {
    doTest("dict[str, str | int | float | Any]",
           """
             from typing import Dict, Union

             JsonDict = Dict[str, Union[str, int, float, 'JsonDict']]

             def f(x: JsonDict):
                 expr = x""");
  }

  // PY-18386
  public void testRecursiveType3() {
    doTest("str | int | Any",
           """
             from typing import Union

             Type1 = Union[str, 'Type2']
             Type2 = Union[int, Type1]

             expr = None # type: Type1""");
  }

  // PY-19858
  public void testGetListItemByIntegral() {
    doTest("list",
           """
             from typing import List

             def foo(x: List[List]):
                 expr = x[0]
             """);
  }

  // PY-19858
  public void testGetListItemByIndirectIntegral() {
    doTest("list",
           """
             from typing import List

             def foo(x: List[List]):
                 y = 0
                 expr = x[y]
             """);
  }

  // PY-19858
  public void testGetSublistBySlice() {
    doTest("list[list]",
           """
             from typing import List

             def foo(x: List[List]):
                 expr = x[1:3]
             """);
  }

  // PY-19858
  public void testGetSublistByIndirectSlice() {
    doTest("list[list]",
           """
             from typing import List

             def foo(x: List[List]):
                 y = slice(1, 3)
                 expr = x[y]
             """);
  }

  // PY-19858
  public void testGetListItemByUnknown() {
    doTest("list | list[list]",
           """
             from typing import List

             def foo(x: List[List]):
                 expr = x[y]
             """);
  }

  public void testGetListOfListsItemByIntegral() {
    doTest("Any",
           """
             from typing import List

             def foo(x: List[List]):
                 sublist = x[0]
                 expr = sublist[0]
             """);
  }

  public void testLocalVariableAnnotation() {
    doTest("int",
           """
             def f():
                 x: int = undefined()
                 expr = x""");
  }

  // PY-21864
  public void testLocalVariableAnnotationAheadOfTimeWithTarget() {
    doTest("int",
           """
             x: int
             with foo() as x:
                 expr = x
             """);
  }

  // PY-21864
  public void testTopLevelVariableAnnotationAheadOfTimeInAnotherFileWithTarget() {
    doMultiFileStubAwareTest("int",
                             """
                               from other import x

                               expr = x""");
  }

  public void testLocalVariableAnnotationAheadOfTimeForTarget() {
    doTest("int",
           """
             x: int
             for x in foo():
                 expr = x
             """);
  }

  // PY-21864
  public void testTopLevelVariableAnnotationAheadOfTimeInAnotherFileForTarget() {
    doMultiFileStubAwareTest("int",
                             """
                               from other import x

                               expr = x""");
  }

  // PY-21864
  public void testLocalVariableAnnotationAheadOfTimeUnpackingTarget() {
    doTest("int",
           """
             x: int
             x, y = foo()
             expr = x""");
  }

  // PY-21864
  public void testTopLevelVariableAnnotationAheadOfTimeInAnotherFileUnpackingTarget() {
    doMultiFileStubAwareTest("int",
                             """
                               from other import x

                               expr = x""");
  }

  // PY-21864
  public void testLocalVariableAnnotationAheadOfTimeOnlyFirstHintConsidered() {
    doTest("int",
           """
             x: int
             x = foo()
             x: str
             x = baz()
             expr = x""");
  }

  // PY-16412
  public void testLocalVariableAnnotationAheadOfTimeExplicitAny() {
    doTest("Any",
           """
             from typing import Any

             def func(x):
                 var: Any
                 var = x
                 expr = var
             """);
  }

  // PY-28032
  public void testClassAttributeAnnotationExplicitAny() {
    doTest("Any",
           """
             from typing import Any

             class C:
                 attr: Any = None
                \s
                 def m(self, x):
                     self.attr = x
                     expr = self.attr""");
  }

  // PY-21864
  public void testClassAttributeAnnotationAheadOfTimeInAnotherFile() {
    doMultiFileStubAwareTest("int",
                             """
                               from other import C

                               expr = C().attr""");
  }

  public void testInstanceAttributeAnnotation() {
    doTest("int",
           """
             class C:
                 attr: int
                \s
             expr = C().attr""");
  }

  public void testIllegalAnnotationTargets() {
    doTest("tuple[Any, int, Any, Any]",
           """
             (w, _): Tuple[int, Any]
             ((x)): int
             y: bool = z = undefined()
             expr = (w, x, y, z)
             """);
  }

  // PY-19723
  public void testAnnotatedPositionalArgs() {
    doTest("tuple[str, ...]",
           """
             def foo(*args: str):
                 expr = args
             """);
  }

  // PY-19723
  public void testAnnotatedKeywordArgs() {
    doTest("dict[str, int]",
           """
             def foo(**kwargs: int):
                 expr = kwargs
             """);
  }

  // PY-19723
  public void testTypeCommentedPositionalArgs() {
    doTest("tuple[str, ...]",
           """
             def foo(*args  # type: str
             ):
                 expr = args
             """);
  }

  // PY-19723
  public void testTypeCommentedKeywordArgs() {
    doTest("dict[str, int]",
           """
             def foo(**kwargs  # type: int
             ):
                 expr = kwargs
             """);
  }

  public void testGenericInheritedSpecificAndGenericParameters() {
    doTest("C[float]",
           """
             from typing import TypeVar, Generic, Tuple, Iterator, Iterable

             T = TypeVar('T')

             class B(Generic[T]):
                 pass

             class C(B[Tuple[int, T]], Generic[T]):
                 def __init__(self, x: T) -> None:
                     pass

             expr = C(3.14)
             """);
  }

  public void testAsyncGeneratorAnnotation() {
    doTest("AsyncGenerator[int, str]",
           """
             from typing import AsyncGenerator

             async def g() -> AsyncGenerator[int, str]:
                 s = (yield 42)
                \s
             expr = g()""");
  }

  public void testCoroutineReturnsGenerator() {
    doTest("Coroutine[Any, Any, Generator[int, Any, Any]]",
           """
             from typing import Generator

             async def coroutine() -> Generator[int, Any, Any]:
                 def gen():
                     yield 42
                \s
                 return gen()
                \s
             expr = coroutine()""");
  }

  public void testGenericRenamedParameter() {
    doTest("int",
           """
             from typing import TypeVar, Generic

             T = TypeVar('T')
             V = TypeVar('V')

             class B(Generic[V]):
                 def get() -> V:
                     pass

             class C(B[T]):
                 def __init__(self, x: T) -> None:
                     pass

             expr = C(0).get()
             """);
  }

  // PY-27627
  public void testExplicitlyParametrizedGenericClassInstance() {
    doTest("Node[int]",
           """
             from typing import TypeVar, Generic, List

             T = TypeVar('T')
             class Node(Generic[T]):
                 def __init__(self, children : List[T]):
                     self.children = children
             expr = Node[int]()""");
  }

  // PY-27627
  public void testMultiTypeExplicitlyParametrizedGenericClassInstance() {
    doTest("float",
           """
             from typing import TypeVar, Generic

             T = TypeVar('T')
             V = TypeVar('V')
             Z = TypeVar('Z')

             class FirstType(Generic[T]): pass
             class SecondType(Generic[V]): pass
             class ThirdType(Generic[Z]): pass

             class Clazz(FirstType[T], SecondType[V], ThirdType[Z]):
                 first: T
                 second: V
                 third: Z

                 def __init__(self):
                     pass

             node = Clazz[str, int, float]()
             expr = node.third""");
  }

  // PY-27627
  public void testExplicitlyParametrizedGenericClassInstanceTypizationPriority() {
    doTest("Node[str]",
           """
             from typing import TypeVar, Generic, List

             T = TypeVar('T')
             class Node(Generic[T]):
                 def __init__(self, children : List[T]):
                     self.children = children
             expr = Node[str]([1,2,3])""");
  }

  // PY-27627
  public void testItemLookupNotResolvedAsParametrizedClassInstance() {
    doTest("tuple[()]",
           """
             d = {
                 int: lambda: ()
             }
             expr = d[int]()""");
  }

  // PY-20057
  public void testClassObjectType() {
    doTest("type[MyClass]",
           """
             from typing import Type

             class MyClass:
                 pass

             def f(x: Type[MyClass]):\s
                 expr = x""");
  }

  // PY-20057
  public void testConstrainedClassObjectTypeOfParam() {
    doTest("type[T]",
           """
             from typing import Type, TypeVar

             T = TypeVar('T', bound=int)

             def f(x: Type[T]):
                 expr = x""");
  }

  // PY-20057
  public void testFunctionCreatesInstanceFromType() {
    doTest("int",
           """
             from typing import Type, TypeVar

             T = TypeVar('T')

             def f(x: Type[T]) -> T:
                 return x()

             expr = f(int)""");
  }

  // PY-20057
  public void testFunctionReturnsTypeOfInstance() {
    doTest("type[int]",
           """
             from typing import Type, TypeVar

             T = TypeVar('T')

             def f(x: T) -> Type[T]:
                 return type(x)
                \s
             expr = f(42)""");
  }

  // PY-20057
  public void testNonParametrizedTypingTypeMapsToBuiltinType() {
    doTest("type",
           """
             from typing import Type

             def f(x: Type):
                 expr = x""");
  }

  // PY-20057
  public void testTypingTypeOfAnyMapsToBuiltinType() {
    doTest("type",
           """
             from typing import Type, Any

             def f(x: Type[Any]):
                 expr = x""");
  }

  // PY-20057
  public void testIllegalTypingTypeFormat() {
    doTest("tuple[Any, Any, Any]",
           """
             from typing import Type, Tuple

             def f(x: Tuple[Type[42], Type[], Type[unresolved]]):
                 expr = x""");
  }

  // PY-20057
  public void testUnionOfClassObjectTypes() {
    doTest("type[int | str]",
           """
             from typing import Type, Union

             def f(x: Type[Union[int, str]]):
                 expr = x""");
  }

  // PY-23053
  public void testUnboundGenericMatchesClassObjectTypes() {
    doTest("type[str]",
           """
             from typing import Generic, TypeVar

             T = TypeVar('T')

             class Holder(Generic[T]):
                 def __init__(self, value: T):
                     self._value = value

                 def get(self) -> T:
                     return self._value

             expr = Holder(str).get()
             """);
  }

  // PY-23053
  public void testListContainingClasses() {
    doTest("type[str]",
           "xs = [str]\n" +
           "expr = xs.pop()");
  }

  public void testGenericUserFunctionWithManyParamsAndNestedCall() {
    doTest("tuple[bool, int, str]",
           """
             from typing import TypeVar

             T = TypeVar('T')
             U = TypeVar('U')
             V = TypeVar('V')

             def myid(x: T) -> T:
                 pass

             def f(x: T, y: U, z: V):
                 return myid(x), myid(y), myid(z)

             expr = f(True, 1, 'foo')
             """);
  }

  // PY-24260
  public void testGenericClassParameterTakenFromGenericClassObject() {
    doTest("MyClass[T]",
           """
             from typing import TypeVar, Generic, Type

             T = TypeVar("T")

             class MyClass(Generic[T]):
                 def __init__(self, type: Type[T]):
                     pass

             def f(x: Type[T]):
                 expr = MyClass(x)
             """);
  }

  // PY-18816
  public void testLocalTypeAlias() {
    doTest("int",
           """
             def func(g):
                 Alias = int
                 expr: Alias = g()""");
  }

  // TODO same test for variable type comments
  // PY-18816
  public void testLocalTypeAliasInFunctionTypeComment() {
    doTest("int",
           """
             def func():
                 Alias = int
                 def g(x):
                     # type: (Alias) -> None
                     expr = x
             """);
  }

  // PY-24729
  public void testAnnotatedInstanceAttributeReferenceOutsideClass() {
    doTest("int",
           """
             class C:
                 attr: int

                 def __init__(self):
                     self.attr = 'foo'

             expr = C().attr
             """);
  }

  // PY-24729
  public void testAnnotatedInstanceAttributeReferenceInsideClass() {
    doTest("int",
           """
             class C:
                 attr: int

                 def __init__(self):
                     self.attr = 'foo'
                    \s
                 def m(self):
                     expr = self.attr
             """);
  }

  // PY-24729
  public void testAnnotatedInstanceAttributeInOtherFile() {
    doMultiFileStubAwareTest("int",
                             """
                               from other import C

                               expr = C().attr""");
  }

  // PY-24990
  public void testSelfAnnotationSameClassInstance() {
    doTest("C",
           """
             from typing import TypeVar

             T = TypeVar('T')

             class C:
                 def method(self: T) -> T:
                     pass

             expr = C().method()""");
  }

  // PY-24990
  public void testSelfAnnotationSubclassInstance() {
    doTest("D",
           """
             from typing import TypeVar

             T = TypeVar('T')

             class C:
                 def method(self: T) -> T:
                     pass

             class D(C):
                 pass

             expr = D().method()""");
  }

  // PY-24990
  public void testClsAnnotationSameClassInstance() {
    doTest("C",
           """
             from typing import TypeVar, Type

             T = TypeVar('T')

             class C:
                 @classmethod
                 def factory(cls: Type[T]) -> T:
                     pass

             expr = C.factory()""");
  }

  // PY-24990
  public void testClsAnnotationSubclassInstance() {
    doTest("D",
           """
             from typing import TypeVar, Type

             T = TypeVar('T')

             class C:
                 @classmethod
                 def factory(cls: Type[T]) -> T:
                     pass

             class D(C):\s
                 pass

             expr = D.factory()""");
  }

  // PY-24990
  public void testClsAnnotationClassMethodCalledOnInstance() {
    doTest("D",
           """
             from typing import TypeVar, Type

             T = TypeVar('T')

             class C:
                 @classmethod
                 def factory(cls: Type[T]) -> T:
                     pass

             class D(C):\s
                 pass

             expr = D().factory()""");
  }

  // PY-24990
  public void testSelfAnnotationReceiverUnionType() {
    doTest("A | B",
           """
             from typing import TypeVar

             T = TypeVar('T')

             class Base:
                 def method(self: T) -> T:
                     pass

             class A(Base):
                 pass

             class B(Base):\s
                 pass

             expr = (A() or B()).method()""");
  }

  // PY-24990
  public void _testClsAnnotationReceiverUnionType() {
    doTest("Union[A, B]",
           """
             from typing import TypeVar, Type

             T = TypeVar('T')

             class Base:
                 @classmethod
                 def factory(cls: Type[T]) -> T:
                     pass

             class A(Base):
                 pass

             class B(Base):
                 pass

             expr = (A or B).factory()""");
  }

  // PY-24990
  public void testClsAnnotationReceiverUnionTypeClassMethodCalledOnMixedInstanceClassObject() {
    doTest("A",
           """
             from typing import TypeVar, Type

             T = TypeVar('T')

             class Base:
                 @classmethod
                 def factory(cls: Type[T]) -> T:
                     pass

             class A(Base):
                 pass

             expr = (A or A()).factory()""");
  }

  // PY-24990
  public void testSelfAnnotationInstanceMethodCalledOnClassObject() {
    doTest("D",
           """
             from typing import TypeVar

             T = TypeVar('T')

             class C:
                 def method(self: T) -> T:
                     pass

             class D(C):
                 pass

             expr = C.method(D())""");
  }

  // PY-24990
  public void testSelfAnnotationInTypeCommentSameClassInstance() {
    doTest("C",
           """
             from typing import TypeVar

             T = TypeVar('T')

             class C:
                 def method(self):
                     # type: (T) -> T
                     pass

             expr = C().method()""");
  }

  // PY-24990
  public void testSelfAnnotationInTypeCommentSubclassInstance() {
    doTest("D",
           """
             from typing import TypeVar

             T = TypeVar('T')

             class C:
                 def method(self):
                     # type: (T) -> T
                     pass

             class D(C):
                 pass

             expr = D().method()""");
  }

  // PY-24990
  public void testClsAnnotationInTypeCommentSameClassInstance() {
    doTest("C",
           """
             from typing import TypeVar, Type

             T = TypeVar('T')

             class C:
                 @classmethod
                 def factory(cls):
                     # type: (Type[T]) -> T
                     pass

             expr = C.factory()""");
  }

  // PY-24990
  public void testClsAnnotationInTypeCommentSubclassInstance() {
    doTest("D",
           """
             from typing import TypeVar, Type

             T = TypeVar('T')

             class C:
                 @classmethod
                 def factory(cls):
                     # type: (Type[T]) -> T
                     pass

             class D(C):\s
                 pass

             expr = D.factory()""");
  }

  // PY-31004
  public void testRecursiveTypeAliasInAnotherFile() {
    doMultiFileStubAwareTest("list[Any] | int",
                             """
                               from other import MyType

                               expr: MyType = ...""");
  }

  // PY-31146
  public void testNoneTypeInAnotherFile() {
    doMultiFileStubAwareTest("(int) -> None",
                             """
                               from other import MyType

                               expr: MyType = ...

                               """);
  }

  // PY-34478
  public void testTrivialTypeAliasInAnotherFile() {
    doMultiFileStubAwareTest("str",
                             """
                               from other import alias

                               expr: alias""");
  }

  // PY-34478
  public void testTrivialUnresolvedTypeAliasInAnotherFile() {
    doMultiFileStubAwareTest("Any",
                             """
                               from other import alias

                               expr: alias""");
  }

  // PY-34478
  public void testTrivialRecursiveTypeAliasInAnotherFile() {
    doMultiFileStubAwareTest("Any",
                             """
                               from other import alias

                               expr: alias""");
  }

  public void testGenericSubstitutionInDeepHierarchy() {
    doTest("int",
           """
             from typing import Generic, TypeVar

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Root(Generic[T1, T2]):
                 def m(self) -> T2:
                     pass

             class Base3(Root[T1, int]):
                 pass

             class Base2(Base3[T1]):
                 pass

             class Base1(Base2[T1]):
                 pass

             class Sub(Base1[T1]):
                 pass

             expr = Sub().m()
             """);
  }

  // PY-35235
  public void testNoStringLiteralInjectionForTypingLiteral() {
    doTestNoInjectedText("""
                           from typing import Literal
                           a: Literal["f<caret>oo"]
                           """);

    doTestNoInjectedText("""
                           from typing import Literal
                           a: Literal[42, "f<caret>oo", True]
                           """);

    doTestNoInjectedText("""
                           from typing import Literal
                           MyType = Literal[42, "f<caret>oo", True]
                           a: MyType
                           """);

    doTestNoInjectedText("""
                           from typing import Literal, TypeAlias
                           MyType: TypeAlias = Literal[42, "f<caret>oo", True]
                           """);

    doTestNoInjectedText("""
                           import typing
                           a: typing.Literal["f<caret>oo"]
                           """);
  }

  // PY-41847
  public void testNoStringLiteralInjectionForTypingAnnotated() {
    doTestNoInjectedText("""
                           from typing import Annotated
                           MyType = Annotated[str, "f<caret>oo", True]
                           a: MyType
                           """);

    doTestNoInjectedText("""
                           from typing import Annotated
                           a: Annotated[int, "f<caret>oo", True]
                           """);

    doTestInjectedText("from typing import Annotated\n" +
                       "a: Annotated['Forward<caret>Reference', 'foo']",
                       "ForwardReference");
  }

  // PY-41847
  public void testTypingAnnotated() {
    doTest("int",
           """
             from typing import Annotated
             A = Annotated[int, 'Some constraint']
             expr: A""");
    doTest("int",
           "from typing_extensions import Annotated\n" +
           "expr: Annotated[int, 'Some constraint'] = '5'");
    doMultiFileStubAwareTest("int",
                             "from annotated import A\n" +
                             "expr: A = 'str'");
  }

  // PY-35370
  public void testAnyArgumentsCallableInTypeComment() {
    doTestInjectedText("from typing import Callable\n" +
                       "a = b  # type: Call<caret>able[..., int]",
                       "Callable[..., int]");
  }

  // PY-42334
  public void testExplicitTypeAliasItselfHasAnyType() {
    doTest("Any",
           """
             from typing import TypeAlias

             expr: TypeAlias = int
             """);
  }

  // PY-29257
  public void testParameterizedTypeAliasForPartiallyGenericType() {
    doTest("dict[str, int]",
           """
             from typing import TypeVar
             T = TypeVar('T')
             dict_t1 = dict[str, T]
             expr: dict_t1[int]""");

    doTest("dict[str, int]",
           """
             from typing import TypeVar
             T = TypeVar('T')
             dict_t1 = dict[T, int]
             expr: dict_t1[str]""");
  }

  // PY-49582
  public void testParameterizedTypeAliasForGenericUnion() {
    doTest("str | Awaitable[str] | None",
           """
             from typing import Awaitable, Optional, TypeVar, Union
             T = TypeVar('T')
             Input = Union[T, Awaitable[T]]

             def f(expr: Optional[Input[str]]):
                 pass
             """);
  }

  // PY-29257
  public void testParameterizedTypeAliasPreservesOrderOfTypeParameters() {
    doTest("dict[str, Any]",
           """
             from typing import TypeVar
             T1 = TypeVar('T1')
             T2 = TypeVar('T2')
             Alias = dict[T1, T2]
             expr: Alias[str]""");

    doTest("dict[str, Any]",
           """
             from typing import TypeVar
             T1 = TypeVar('T1')
             T2 = TypeVar('T2')
             Alias = dict[T2, T1]
             expr: Alias[str]""");
  }

  // PY-29257
  public void testGenericTypeAliasParameterizedWithExplicitAny() {
    doTest("dict[Any, str]",
           """
             from typing import TypeVar
             T1 = TypeVar('T1')
             T2 = TypeVar('T2')
             Alias = dict[T1, T2]
             expr: Alias[Any, str]""");
  }

  // PY-29257
  public void testGenericTypeAliasParameterizedInTwoSteps() {
    doTest("dict[int, str]",
           """
             from typing import TypeVar
             T1 = TypeVar('T1')
             T2 = TypeVar('T2')
             Alias1 = dict[T1, T2]
             Alias2 = Alias1[int, T2]
             expr: Alias2[str]""");
  }

  // PY-44905
  public void testGenericTypeAliasToAnnotated() {
    doTest("int",
           """
             from typing import Annotated, TypeVar
             marker = object()
             T = TypeVar("T")
             Inject = Annotated[T, marker]
             expr: Inject[int]""");
  }

  // PY-29257
  public void testGenericTypeAliasForTuple() {
    doTest("tuple[int, int]",
           """
             from typing import TypeVar
             T = TypeVar('T')
             Pair = tuple[T, T]
             expr: Pair[int]""");
  }

  // PY-29257
  public void testGenericAliasParametersCannotBeOverridden() {
    doTest("list[int]",
           "Alias = list[int]\n" +
           "expr: Alias[str]");
  }

  // PY-44974
  public void testBitwiseOrUnionIsInstance() {
    doTest("str | dict | int",
           """
             a = [42]
             if isinstance(a, str | dict | int):
                 expr = a""");
  }

  // PY-44974
  public void testBitwiseOrUnionIsSubclass() {
    doTest("type[str | dict | int]",
           """
             a = list
             if issubclass(a, str | dict | int):
                 expr = a""");
  }

  // PY-79861
  public void testWalrusIsSubclass() {
    doTest("type[str | dict | int]",
           """
             if issubclass(a := list, str | dict | int):
                 expr = a""");
  }

  // PY-79861
  public void testWalrusCallable() {
    doTest("type[Callable]",
           """
             if callable(a := 42):
                 expr = a""");
  }

  // PY-44974
  public void testBitwiseOrUnionIsInstanceIntNone() {
    doTest("int | None",
           """
             a = [42]
             if isinstance(a, int | None):
                 expr = a""");
  }

  // PY-44974
  public void testBitwiseOrUnionIsInstanceNoneInt() {
    doTest("int | None",
           """
             a = [42]
             if isinstance(a, None | int):
                 expr = a""");
  }

  // PY-79861
  public void testWalrusIsInstance() {
    doTest("int",
           """
             if isinstance((a := [42]), int):
                 expr = a""");
  }

  // PY-44974
  public void testBitwiseOrUnionIsInstanceUnionInTuple() {
    doTest("str | list | dict | bool | None",
           """
             from typing import Literal
             a: Literal[42] = 42
             if isinstance(a, (str, (list | dict), bool | None)):
                 expr = a""");
  }

  // PY-44974
  public void testBitwiseOrUnionOfUnionsIsInstance() {
    doTest("dict | str | bool | list",
           """
             from typing import Union, Literal
             a: Literal[42] = 42
             if isinstance(a, Union[dict, Union[str, Union[bool, list]]]):
                 expr = a""");
  }

  // PY-44974
  public void testBitwiseOrUnionWithFromFutureImport() {
    runWithLanguageLevel(LanguageLevel.PYTHON39, () -> {
      doTest("int | str",
             """
               from __future__ import annotations
               if something:
                   x: int
               else:
                   x: str
               expr = x
               """);
    });
  }

  // PY-44974
  public void testBitwiseOrUnionWithoutFromFutureImport() {
    runWithLanguageLevel(LanguageLevel.PYTHON39, () -> {
      doTest("Union[int, str]",
             """
               if something:
                   x: int
               else:
                   x: str
               expr = x
               """);
    });
  }

  // PY-44974
  public void testBitwiseOrUnionParenthesizedUnionOfUnions() {
    doTest("int | list | dict | float | str",
           """
             bar: int | ((list | dict) | (float | str)) = ""
             expr = bar
             """);
  }

  // PY-44974
  public void testBitwiseOrOperatorOverload() {
    doTest("int",
           """
             class A:
               def __or__(self, other) -> int: return 5
              \s
             expr = A() | A()""");
  }

  public void testClassVarTypeResolvedFromAnnotation() {
    doTest("int",
           """
             from typing import ClassVar
             class A:
                 x: ClassVar[int] = 1
             expr = A.x""");
  }

  public void testClassVarTypeResolvedFromTypeComment() {
    doTest("int",
           """
             from typing import ClassVar
             class A:
                 x = 1  # type: ClassVar[int]
             expr = A.x""");
  }

  // PY-53104
  public void testMethodReturnSelf() {
    doTest("B",
           """
             from typing import Self

             class A:
                 def foo(self) -> Self:
                     ...
             class B(A):
                 pass
             expr = B().foo()""");
  }

  // PY-53104
  public void testMethodReturnListSelf() {
    doTest("list[B]",
           """
             from typing import Self

             class A:
                 def foo(self) -> list[Self]:
                     ...
             class B(A):
                 pass:
                     ...
             expr = B().foo()""");
  }

  // PY-53104
  public void testClassMethodReturnSelf() {
    doTest("Circle",
           """
             from typing import Self


             class Shape:
                 @classmethod
                 def from_config(cls, config: dict[str, float]) -> Self:
                     return cls(config["scale"])


             class Circle(Shape):
                 pass


             expr = Circle.from_config({})
             """);
  }

  // PY-53104
  public void testClassMethodReturnSelfNestedClass() {
    doTest("Circle",
           """
             from typing import Self


             class OuterClass:
                 class Shape:
                     @classmethod
                     def from_config(cls, config: dict[str, float]) -> Self:
                         return cls(config["scale"])

                 class Circle(Shape):
                     pass


             expr = OuterClass.Circle.from_config({})
             """);
  }

  // PY-53104
  public void testNoUnstubInCalculateSelfTypeInFunctionDefinedInImportedFile() {
    doMultiFileStubAwareTest("Clazz",
                             """
                               from other import Clazz
                               clz = Clazz()
                               expr = clz.foo()
                               """);
  }

  // PY-53104
  public void testMatchSelfUnionType() {
    doTest("C",
           """
             from typing import Self


             class C:
                 def method(self) -> Self:
                     return self


             if bool():
                 x = 42
             else:
                 x = C()

             expr = x.method()""");
  }

  // PY-53105
  public void testGenericVariadicType() {
    doTest("tuple[*Shape]",
           """
             from typing import Generic, TypeVarTuple, Tuple

             Shape = TypeVarTuple('Shape')

             t: Tuple[*Shape]
             expr = t""");
  }

  // PY-53105
  public void testGenericVariadicByCallable() {
    doTest("tuple[int, str]",
           """
             from typing import TypeVar, TypeVarTuple, Callable, Tuple

             Ts = TypeVarTuple('Ts')


             def foo(f: Callable[[*Ts], Tuple[*Ts]]) -> Tuple[*Ts]: ...
             def bar(a: int, b: str) -> Tuple[int, str]: ...


             expr = foo(bar)
             """);
  }

  // PY-53105
  public void testGenericVariadicByCallablePrefixSuffix() {
    doTest("tuple[str, str, float, int, bool]",
           """
             from typing import TypeVar, TypeVarTuple, Callable, Tuple

             T = TypeVar('T')
             Ts = TypeVarTuple('Ts')


             def foo(f: Callable[[int, *Ts, T], Tuple[T, *Ts]]) -> Tuple[str, *Ts, int, T]: ...
             def bar(a: int, b: str, c: float, d: bool) -> Tuple[bool, str, float]: ...


             expr = foo(bar)
             """);
  }

  // PY-53105
  public void testGenericVariadicClass() {
    doTest("A[float, bool, list[str]]",
           """
             from typing import TypeVarTuple, Generic, Tuple

             Ts = TypeVarTuple('Ts')


             class A(Generic[*Ts]):
                 def __init__(self, value: Tuple[int, *Ts]) -> None:
                     self.field: Tuple[int, *Ts] = value


             tpl = (42, 1.1, True, ['42'])
             expr = A(tpl)
             """);
  }

  // PY-53105
  public void testGenericVariadicClassField() {
    doTest("tuple[int, float, bool, list[str]]",
           """
             from typing import TypeVarTuple, Generic, Tuple

             Ts = TypeVarTuple('Ts')


             class A(Generic[*Ts]):
                 def __init__(self, value: Tuple[int, *Ts]) -> None:
                     self.field: Tuple[int, *Ts] = value


             tpl = (42, 1.1, True, ['42'])
             a = A(tpl)
             expr = a.field
             """);
  }

  // PY-53105
  public void testGenericVariadicClassMethod() {
    doTest("tuple[int, bool, float, str]",
           """
             from typing import TypeVarTuple, Generic, Tuple

             Ts = TypeVarTuple('Ts')


             class A(Generic[*Ts]):
                 def __init__(self, value: Tuple[*Ts]) -> None:
                     ...

                 def foo(self) -> Tuple[int, *Ts, str]:
                     ...


             tpl = (True, 1.1)
             a = A(tpl)
             expr = a.foo()

             """);
  }

  // PY-53105
  public void testGenericVariadicClassMethodPlus() {
    doTest("A[int, str, bool, int]",
           """
             from __future__ import annotations
             from typing import TypeVarTuple, Generic, Tuple, TypeVar

             T = TypeVar('T')
             Ts = TypeVarTuple('Ts')


             class A(Generic[T, *Ts]):
                 def __init__(self, t: T, *args: *Ts) -> None:
                     ...

                 def __add__(self, other: A[T, *Ts]) -> A[T, *Ts, T]:
                     ...


             a = A(1, '', True)
             b = A(1, '', True)
             expr = a + b
             """);
  }

  // PY-53105
  public void testGenericVariadicAndGenericClass() {
    doTest("A[int | str, int | str, list[int]]",
           """
             from __future__ import annotations
             from typing import TypeVarTuple, Generic, Tuple, TypeVar

             T = TypeVar('T')
             T1 = TypeVar('T1')
             Ts = TypeVarTuple('Ts')


             class A(Generic[T, *Ts, T1]):
                 def __init__(self, t: T, tpl: Tuple[*Ts], t1: T1) -> None:
                     ...


             x: int | str
             expr = A(x, (x,), [1])
             """);
  }

  // PY-53105
  public void testGenericVariadicClassMethodAddAxisPrefix() {
    doTest("Array[str, int, bool]",
           """
             from __future__ import annotations
             from typing import Generic, TypeVarTuple, Tuple, NewType, TypeVar

             T = TypeVar('T')
             Shape = TypeVarTuple('Shape')


             class Array(Generic[*Shape]):
                 def __init__(self, shape: Tuple[*Shape]):
                     self._shape: Tuple[*Shape] = shape

                 def add_axis_prefix(self, t: T) -> Array[T, *Shape]: ...


             shape = (42, True)
             arr: Array[int, bool] = Array(shape)
             expr = arr.add_axis_prefix('')
             """);
  }

  // PY-53105
  public void testGenericVariadicClassMethodAddAxisSuffix() {
    doTest("Array[list[int], bool, str]",
           """
             from __future__ import annotations
             from typing import Generic, TypeVarTuple, Tuple, NewType, TypeVar

             T = TypeVar('T')
             Shape = TypeVarTuple('Shape')


             class Array(Generic[*Shape]):
                 def __init__(self, shape: Tuple[*Shape]):
                     self._shape: Tuple[*Shape] = shape

                 def add_axis_suffix(self, t: T) -> Array[*Shape, T]: ...


             shape = ([42], True)
             arr: Array[list[int], bool] = Array(shape)
             expr = arr.add_axis_suffix('42')
             """);
  }

  // PY-53105
  public void testGenericVariadicClassMethodAddAxisPrefixAndSuffix() {
    doTest("Array[str, dict[int, str], int, str, list[int], bool]",
           """
             from __future__ import annotations
             from typing import Generic, TypeVarTuple, Tuple, NewType, TypeVar

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')
             T3 = TypeVar('T3')
             T4 = TypeVar('T4')
             Shape = TypeVarTuple('Shape')


             class Array(Generic[*Shape]):
                 def __init__(self, shape: Tuple[*Shape]):
                     self._shape: Tuple[*Shape] = shape

                 def add_axis_prefix_suffix(self, t1: T1, t2: T2, t3: T3, t4: T4) -> Array[T3, T2, *Shape, T1, T4]: ...


             shape = (42, '42')
             arr: Array[int, str] = Array(shape)
             expr = arr.add_axis_prefix_suffix([42], {42: '42'}, '42', True)
             """);
  }

  // PY-53105
  public void testGenericVariadicFunctionAddPrefixAndSuffix() {
    doTest("Array[int, list[int], bool, str]",
           """
             from typing import Generic, TypeVarTuple, NewType, Tuple

             Ts = TypeVarTuple('Ts')


             class Array(Generic[*Ts]):
                 def __init__(self, shape: Tuple[*Ts]):
                     ...


             def add_suf_pref(x: Array[*Ts]) -> Array[int, *Ts, str]:
                 ...


             ts = ([42], True)
             arr = Array(ts)
             expr = add_suf_pref(arr)
             """);
  }

  // PY-53105
  public void testGenericVariadicFunctionDeletePrefixAndSuffix() {
    doTest("Array[list[int], bool]",
           """
             from typing import Generic, TypeVarTuple, NewType, Tuple

             Ts = TypeVarTuple('Ts')


             class Array(Generic[*Ts]):
                 def __init__(self, shape: Tuple[*Ts]):
                     ...


             def del_suf_pref(x: Array[int, *Ts, str]) -> Array[*Ts]:
                 ...


             ts = (42, [42], True, '42')
             arr = Array(ts)
             expr = del_suf_pref(arr)
             """);
  }

  // PY-53105
  public void testGenericVariadicStarArgs() {
    doTest("tuple[int, str]",
           """
             from typing import TypeVarTuple, Tuple

             Ts = TypeVarTuple('Ts')


             def args_to_tuple(*args: *Ts) -> Tuple[*Ts]: ...


             expr = args_to_tuple(1, 'a')
             """);
  }

  // PY-53105
  public void testGenericVariadicStarArgsOfGenericVariadics() {
    doTest("tuple[int, str]",
           """
             from typing import Tuple, TypeVarTuple

             Ts = TypeVarTuple('Ts')


             def foo(*args: Tuple[*Ts]) -> Tuple[*Ts]: ...


             expr = foo((0, '1'), (1, '0'))
             """);
  }

  // PY-53105
  public void testGenericVariadicStarArgsPrefixSuffix() {
    doTest("tuple[str, list[Any], dict[Any, Any], bool, int]",
           """
             from typing import TypeVarTuple, Tuple

             Ts = TypeVarTuple('Ts')


             def foo(*args: *Tuple[int, *Ts, str]) -> Tuple[*Ts, int]: ...


             expr = foo(1, '', [], {}, True, '')
             """);
  }

  // PY-53105
  public void testGenericVariadicStarArgsAndTypeVars() {
    doTest("tuple[str, list[int], bool, int]",
           """
             from typing import TypeVarTuple, Tuple, TypeVar

             Ts = TypeVarTuple('Ts')
             T1 = TypeVar('T1')
             T2 = TypeVar('T2')


             def args_to_tuple(t1: T1, t2: T2, *args: *Tuple[T2, *Ts, float]) -> Tuple[T2, *Ts, T1]: ...


             expr = args_to_tuple(1, 'a', 'a', [1], True, 3.3)
             """);
  }

  // PY-53105
  public void testGenericVariadicTypeAlias() {
    doTest("tuple[int, str, bool]",
           """
             from typing import Tuple, TypeVarTuple

             Ts = TypeVarTuple('Ts')

             MyType = Tuple[int, *Ts]

             t: MyType[str, bool]
             expr = t
             """);
  }

  // PY-53105
  public void testGenericVariadicAndGenericTypeAlias() {
    doTest("tuple[int, str, bool, float]",
           """
             from typing import Tuple, TypeVarTuple, TypeVar

             T = TypeVar('T')
             Ts = TypeVarTuple('Ts')

             MyType = Tuple[int, *Ts, T]

             t: MyType[str, bool, float]
             expr = t
             """);
  }

  // PY-53105
  public void testGenericVariadicAndGenericConsecutiveTypeAlias() {
    doTest("tuple[int, str, list[str], dict[str, int]]",
           """
             from typing import Tuple, TypeVarTuple, TypeVar

             T = TypeVar('T')
             Ts = TypeVarTuple('Ts')

             MyType = Tuple[int, T, *Ts]
             MyType1 = MyType[str, *Ts]

             t: MyType1[list[str], dict[str, int]]
             expr = t
             """);
  }

  // PY-53105
  public void testChainOfGenericAliasesWithTypeVarTupleReplacedByGenericUnpackedTuple() {
    doTest("tuple[int, str, bool, list[str], dict[str, int]]",
           """
             from typing import Tuple, TypeVarTuple, TypeVar

             T = TypeVar('T')
             Ts = TypeVarTuple('Ts')
             Ts1 = TypeVarTuple('Ts1')

             MyType = Tuple[int, T, *Ts]
             MyType1 = MyType[str, bool, *Ts1]

             t: MyType1[list[str], dict[str, int]]
             expr = t
             """);
  }

  public void testChainOfGenericAliasesWithTypeVarReplacedByGenericType() {
    doTest("tuple[int, list[str]]",
           """
             from typing import Tuple, TypeVarTuple, TypeVar

             T = TypeVar('T')
             T2 = TypeVar('T2')

             MyType = Tuple[int, T]
             MyType1 = MyType[list[T2]]

             t: MyType1[str]
             expr = t
             """);
  }

  // PY-53105
  public void testGenericVariadicsIntersectsSameName() {
    doTest("tuple[int, str, bool, Any]",
           """
             from typing import Tuple, TypeVarTuple, TypeVar

             T = TypeVar('T')
             Ts = TypeVarTuple('Ts')

             MyType = Tuple[int, T, *Ts]
             MyType1 = MyType[str, bool, *Ts]

             t: MyType1[list[str], dict[str, int]]  # first place \s
             expr = t
             """);
  }

  // PY-53105
  public void testGenericVariadicsTupleUnpacking() {
    doTest("tuple[int, str, bool, float]",
           """
           from typing import Tuple, TypeVarTuple, TypeVar
           Ts = TypeVarTuple('Ts')
           MyType = Tuple[int, *Ts]
           t: MyType[*tuple[str, bool, float]]
           expr = t
           """);
  }

  // PY-53105
  public void testVariadicGenericMatchWithHomogeneousGenericVariadicAndOtherTypes() {
    doTest("Array[*tuple[Any, ...], int, str]","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[int, *tuple[Any, ...], int, str] = Array()
                    
                    def expect_variadic_array(x: Array[int, *Shape]) -> Array[*Shape]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testVariadicGenericMatchWithHomogeneousGenericVariadicAndOtherTypesPrefixSuffix() {
    doTest("Array[*tuple[Any, ...], int, float, str]","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[int, float, *tuple[Any, ...], int, str] = Array()
                    
                    def expect_variadic_array(x: Array[int, T, *Shape, T1]) -> Array[*Shape, T, T1]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testVariadicGenericMatchWithHomogeneousGenericVariadicAmbiguousMatchActualGenericFirst() {
    doTest("Array[*tuple[float, ...], int, float, str]", """
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[int, *tuple[float, ...], int, str] = Array()
                    
                    def expect_variadic_array(x: Array[int, T, *Shape, T1]) -> Array[*Shape, T, T1]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testGenericVariadicsNotUnifiedBothAmbiguousMatch() {
    doTest("Array[*tuple[int, ...], int, int, str]", """
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[*tuple[int, ...], int, str] = Array()
                    
                    def expect_variadic_array(x: Array[int, T, *Shape, T1]) -> Array[*Shape, T, T1]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testGenericVariadicsNotUnifiedBothActualHomogeneousGenericFirst() {
    doTest("Array[float, *tuple[float, ...]]","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    T = TypeVar("T")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[*tuple[float, ...]] = Array()
                    
                    def expect_variadic_array(x: Array[T, *Shape]) -> Array[T, *Shape]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testGenericVariadicsNotUnifiedBothActualHomogeneousGenericLast() {
    doTest("Array[*tuple[float, ...], float]","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    T = TypeVar("T")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[*tuple[float, ...]] = Array()
                    
                    def expect_variadic_array(x: Array[*Shape, T]) -> Array[*Shape, T]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testGenericVariadicsNotUnifiedBothActualHomogeneousGenericsBothSides() {
    doTest("Array[float, *tuple[float, ...], float, float]","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    T2 = TypeVar("T2")
                    T3 = TypeVar("T3")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[*tuple[float, ...]] = Array()
                    
                    def expect_variadic_array(x: Array[T1, *Shape, T2, T3]) -> Array[T1, *Shape, T2, T3]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testGenericVariadicsNotUnifiedBothSameExpectedAndActual() {
    doTest("Array[*Shape]","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    Shape1 = TypeVarTuple("Shape1")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[int, *Shape, str] = Array()
                    
                    def expect_variadic_array(x: Array[int, *Shape1, str]) -> Array[*Shape1]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testGenericVariadicsNotUnifiedBothExpectedExpand() {
    doTest("Array[float, *Shape, list[str]]","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    Shape1 = TypeVarTuple("Shape1")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[int, float, *Shape, list[str], str] = Array()
                    
                    def expect_variadic_array(x: Array[int, *Shape1, str]) -> Array[*Shape1]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testGenericVariadicsNotUnifiedBothExpectedExpandTwoArguments() {
    doTest("Any","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    Shape1 = TypeVarTuple("Shape1")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    a: Array[int, float, *Shape, list[str], str] = Array()
                    
                    def expect_variadic_arrays(x: Array[int, *Shape1, str], y: Array[int, float, bool, list[str], str]) -> Array[*Shape1]:
                        print(x, y)
                    
                    expr = expect_variadic_arrays(a, a)
                    """);
  }

  public void testGenericVariadicsNotUnifiedBothExpectedExpandTwoArgumentsGenericVariadic() {
    doTest("Any","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    Shape1 = TypeVarTuple("Shape1")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    a: Array[int, float, *Shape, list[str], str] = Array()
                    
                    def expect_variadic_arrays(x: Array[int, *Shape1, str], y: Array[int, float, *Shape1, list[str], str]) -> Array[*Shape1]:
                        print(x, y)
                    
                    expr = expect_variadic_arrays(a, a)
                    """);
  }

  public void testGenericVariadicsNotUnifiedBothExpectedExpandTwoDifferentArgumentsGenericVariadic() {
    doTest("Any","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    Shape1 = TypeVarTuple("Shape1")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    a: Array[int, float, *Shape, list[str], str] = Array()
                    
                    def expect_variadic_arrays(x: Array[int, *Shape1, str], y: Array[int, float, *Shape, bool, list[str], str]) -> Array[*Shape1] | Array[*Shape]:
                        print(x, y)
                    
                    expr = expect_variadic_arrays(a, a)
                    """);
  }

  // PY-53105
  public void testGenericVariadicsNotUnifiedBothExpectedExpandNotExactLeft() {
    doTest("Any","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    Shape1 = TypeVarTuple("Shape1")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[int, *Shape, list[str], str] = Array()
                    
                    def expect_variadic_array(x: Array[int, float, *Shape1, str]) -> Array[*Shape1]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testGenericVariadicsNotUnifiedBothExpectedExpandNotExactRight() {
    doTest("Any","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    Shape1 = TypeVarTuple("Shape1")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[int, float, *Shape, str] = Array()
                    
                    def expect_variadic_array(x: Array[int, *Shape1, int, str]) -> Array[*Shape1]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testGenericVariadicsNotUnifiedBothActualSwallowAllExpected() {
    doTest("Any","""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    Shape1 = TypeVarTuple("Shape1")
                    T = TypeVar("T")
                    T1 = TypeVar("T1")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[*Shape] = Array()
                    
                    def expect_variadic_array(x: Array[int, *Shape1, str]) -> Array[*Shape1]:
                        print(x)
                    
                    expr = expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testVariadicGenericClassOverloadedMethods() {
    doTest("Array[str, int]", """
                    from __future__ import annotations
                                     
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import overload
                    
                    Shape = TypeVarTuple("Shape")
                    Axis1 = TypeVar("Axis1")
                    Axis2 = TypeVar("Axis2")
                    Axis3 = TypeVar("Axis3")
                    
                    
                    class Array(Generic[*Shape]):
                       @overload
                       def transpose(self: Array[Axis1, Axis2]) -> Array[Axis2, Axis1]: ...
                    
                       @overload
                       def transpose(self: Array[Axis1, Axis2, Axis3]) -> Array[Axis3, Axis2, Axis1]: ...
                    
                       def transpose(self): ...
                    
                    
                    a: Array[int, str] = Array()
                    
                    expr = a.transpose()
                    """);
  }

  // PY-53105
  public void testVariadicGenericClassOverloadedMethodsSecondMethod() {
    doTest("Array[list[int], str, int]", """
                    from __future__ import annotations
                                     
                    from typing import TypeVarTuple
                    from typing import TypeVar
                    from typing import Generic
                    from typing import overload
                    
                    Shape = TypeVarTuple("Shape")
                    Axis1 = TypeVar("Axis1")
                    Axis2 = TypeVar("Axis2")
                    Axis3 = TypeVar("Axis3")
                    
                    
                    class Array(Generic[*Shape]):
                       @overload
                       def transpose(self: Array[Axis1, Axis2]) -> Array[Axis2, Axis1]: ...
                    
                       @overload
                       def transpose(self: Array[Axis1, Axis2, Axis3]) -> Array[Axis3, Axis2, Axis1]: ...
                    
                       def transpose(self): ...
                    
                    
                    a: Array[int, str, list[int]] = Array()
                    
                    expr = a.transpose()
                    """);
  }

  public void testUnresolvedReturnTypeNotOverridenByAncestorAnnotation() {
    doTest("Any",
           """
             class Super:
                 def m(self) -> int:
                     ...
             class Sub(Super):
                 def m(self) -> Unresolved:
                     ...
             expr = Sub().m()
             """);
  }

  public void testGenericProtocolUnificationSameTypeVar() {
    doTest("list[int]",
           """
             from typing import Protocol
             from typing import TypeVar

             T = TypeVar('T', covariant=True)

             class SupportsIter(Protocol[T]):
                 def __iter__(self) -> T:
                     pass

             def my_iter(x: SupportsIter[T]) -> T:
                 pass

             class MyList:
                 def __iter__(self) -> list[int]:
                     pass

             expr = my_iter(MyList())""");
  }

  public void testGenericProtocolUnificationSeparateTypeVar() {
    doTest("list[int]",
           """
             from typing import Protocol
             from typing import TypeVar

             T = TypeVar('T', covariant=True)
             T2 = TypeVar('T2')

             class SupportsIter(Protocol[T]):
                 def __iter__(self) -> T:
                     pass

             def my_iter(x: SupportsIter[T2]) -> T2:
                 pass

             class MyList:
                 def __iter__(self) -> list[int]:
                     pass

             expr = my_iter(MyList())""");
  }

  public void testGenericProtocolUnificationGenericImplementation() {
    doTest("int",
           """
             from typing import Generic, Protocol, TypeVar\s

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Fooable(Protocol[T1]):
                 def foo(self) -> T1:
                     ...

             class MyClass(Generic[T2]):
                 def foo(self) -> T2:
                     ...

             def f(x: Fooable[T1]) -> T1:
                 ...

             obj: MyClass[int]
             expr = f(obj)""");
  }

  public void testGenericProtocolUnificationNonGenericImplementationWithGenericSuperclass() {
    doTest("int",
           """
             from typing import Generic, Protocol, TypeVar\s

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Fooable(Protocol[T1]):
                 def foo(self) -> T1:
                     ...

             class Super(Generic[T2]):
                 def foo(self) -> T2:
                     ...

             class MyClass(Super[int]):
                 pass

             def f(x: Fooable[T1]) -> T1:
                 ...

             obj: MyClass
             expr = f(obj)""");
  }

  public void testGenericProtocolUnificationGenericImplementationWithGenericSuperclass() {
    doTest("int",
           """
             from typing import Generic, Protocol, TypeVar\s

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Fooable(Protocol[T1]):
                 def foo(self) -> T1:
                     ...

             class Super(Generic[T2]):
                 def foo(self) -> T2:
                     ...

             class MyClass(Super[T2]):
                 pass

             def f(x: Fooable[T1]) -> T1:
                 ...

             obj: MyClass[int]
             expr = f(obj)""");
  }

  public void testGenericProtocolUnificationGenericImplementationWithGenericSuperclassAndExtraParameter() {
    doTest("int",
           """
             from typing import Generic, Protocol, TypeVar\s

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Fooable(Protocol[T1]):
                 def foo(self) -> T1:
                     ...

             class Super(Generic[T2]):
                 def foo(self) -> T2:
                     ...

             class MyClass(Super[int], Generic[T1]):
                 pass

             def f(x: Fooable[T1]) -> T1:
                 ...

             obj: MyClass[str]
             expr = f(obj)""");
  }


  public void testGenericMethodCallUnification() {
    doTest("int", """
      from typing import Generic, TypeVar
      T = TypeVar("T")

      class Box(Generic[T]):
          def __init__(self, value: T) -> None:
              self.value = value
          def get(self) -> T:
              return self.value

      box = Box(42)
      expr = box.get()
      """);
  }

  // PY-53105
  public void testGenericVariadicMethodCallUnification() {
    doTest("tuple[int, str, float]", """
      from typing import Generic, TypeVarTuple, Tuple
                      
      Ts = TypeVarTuple("Ts")
      
      class Box(Generic[*Ts]):
        def __init__(self, value: Tuple[*Ts]) -> None:
            self.value = value
        def get(self):
            return self.value
      
      box = Box((42, 'a', 3.3))
      expr = box.get()""");
  }

  public void testSingleTypeVarSpecifiedOnInheritance() {
    doTest("str", """
      from typing import Generic, TypeVar

      T = TypeVar("T")

      class Box(Generic[T]):
          pass

      class StrBox(Box[str]):
          pass

      def extract(b: Box[T]) -> T:
          pass

      box = StrBox()
      expr = extract(box)""");
  }

  // PY-53105
  public void testSingleTypeVarTupleSpecifiedOnInheritance() {
    doTest("tuple[str, int]", """
      from typing import Generic, TypeVarTuple, Tuple
            
      Ts = TypeVarTuple("Ts")
            
      class Box(Generic[*Ts]):
          pass
            
      class StrBox(Box[str, int]):
          pass
            
      def extract(b: Box[*Ts]) -> Tuple[*Ts]:
          pass
            
      box = StrBox()
      expr = extract(box)
      """);
  }

  public void testPartialTypeVarSpecializationOnInheritanceInherited() {
    doTest("str",
           """
             from typing import Generic, TypeVar
             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Pair(Generic[T1, T2]):
                 pass

             class StrFirstPair(Pair[str, T2]):
                 def __init__(self, second: T2):
                     pass

             def first(pair: Pair[T1, T2]) -> T1:
                 pass

             expr = first(StrFirstPair(42))""");
  }

  public void testPartialTypeVarSpecializationOnInheritanceInstantiated() {
    doTest("int",
           """
             from typing import Generic, TypeVar
             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Pair(Generic[T1, T2]):
                 pass

             class StrFirstPair(Pair[str, T2]):
                 def __init__(self, second: T2):
                     pass

             def second(pair: Pair[T1, T2]) -> T2:
                 pass

             expr = second(StrFirstPair(42))""");
  }

  public void testTypeVarsNotSpecializedOnInheritanceDistinctTypeVars() {
    doTest("tuple[int, str]",
           """
             from typing import Generic, TypeVar

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')
             T3 = TypeVar('T3')
             T4 = TypeVar('T4')
             T5 = TypeVar('T5')
             T6 = TypeVar('T6')

             class Pair(Generic[T1, T2]):
                 pass

             class PairExt(Pair[T3, T4]):
                 def __init__(self, first: T3, second: T4):
                     pass

             def to_tuple(pair: Pair[T5, T6]) -> tuple[T5, T6]:
                 pass

             expr = to_tuple(PairExt(42, 'foo'))""");
  }

  public void testTypeVarsNotSpecializedOnInheritanceReusedTypeVars() {
    doTest("tuple[int, str]",
           """
             from typing import Generic, TypeVar

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Pair(Generic[T1, T2]):
                 pass

             class PairExt(Pair[T1, T2]):
                 def __init__(self, first: T1, second: T2):
                     pass

             def to_tuple(pair: Pair[T1, T2]) -> tuple[T1, T2]:
                 pass

             expr = to_tuple(PairExt(42, 'foo'))""");
  }

  public void testTypeVarSpecializedOnInheritanceExtraTypeVarAdded() {
    doTest("str",
           """
             from typing import Generic, TypeVar

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')
             T3 = TypeVar('T3')

             class Box(Generic[T1]):
                 pass

             class StrBoxWithExtra(Box[str], Generic[T2]):
                 def __init__(self, extra: T2):
                     self.extra = extra

             def func(b: Box[T3]) -> T3:
                 pass

             box = StrBoxWithExtra(42)
             expr = func(box)""");
  }

  // PY-53105
  public void testTypeVarTupleSpecializedOnInheritanceExtraTypeVarAdded() {
    doTest("tuple[str, int]",
           """
             from typing import Generic, TypeVarTuple, Tuple
                             
             Ts1 = TypeVarTuple('Ts1')
             Ts2 = TypeVarTuple('Ts2')
             Ts3 = TypeVarTuple('Ts3')
             
             class Box(Generic[*Ts1]):
                 pass
             
             class StrBoxWithExtra(Box[str, int], Generic[*Ts2]):
                 def __init__(self, extra: Tuple[*Ts2]):
                     self.extra = extra
             
             def func(b: Box[*Ts3]) -> Tuple[*Ts3]:
                 pass
             
             box = StrBoxWithExtra((42, 'a', 3.3))
             expr = func(box)""");
  }

  public void testGenericClassSpecializesInheritedParameterAndAddsNewOne() {
    doTest("StrBoxWithExtra[int]",
           """
             from typing import Generic, TypeVar

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Box(Generic[T1]):
                 pass

             class StrBoxWithExtra(Box[str], Generic[T2]):
                 def __init__(self, extra: T2):
                     self.extra = extra

             expr = StrBoxWithExtra(42)""");
  }

  // PY-53105
  public void testGenericVariadicClassSpecializesInheritedParameterAndAddsNewOne() {
    doTest("StrBoxWithExtra[int, str, float]",
           """
             from typing import Generic, TypeVarTuple, Tuple
                          
             Ts1 = TypeVarTuple('Ts1')
             Ts2 = TypeVarTuple('Ts2')
                          
             class Box(Generic[*Ts1]):
                 pass
                          
             class StrBoxWithExtra(Box[str], Generic[*Ts2]):
                 def __init__(self, extra: Tuple[*Ts2]):
                     self.extra = extra
                          
             expr = StrBoxWithExtra((42, 'a', 3.3))""");
  }

  // PY-70528
  public void testTypeVarTupleAndUnpackFromTypingExtensions() {
    doTest("tuple[int, str]",
           """
             from typing_extensions import TypeVarTuple, Unpack
             
             Ts = TypeVarTuple("Ts")
             
             def f(*args: Unpack[Ts]) -> tuple[Unpack[Ts]]:
                 ...
             
             expr = f(42, "foo")
             """);
  }

  public void testGenericSelfSpecializationInOverloadedConstructor() {
    doTest("Pair[int, int]",
           """
             from typing import Generic, TypeVar, overload

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Pair(Generic[T1, T2]):
                 @overload
                 def __init__(self: 'Pair[str, str]', value: str):
                     pass

                 @overload
                 def __init__(self: 'Pair[int, int]', value: int):
                     pass

             expr = Pair(42)""");
  }

  public void testIterResultOnIterable() {
    doTest("Iterator[int]",
           """
             from typing import Iterable, TypeVar

             T = TypeVar('T')

             xs: Iterable[int]
             expr = iter(xs)
             """);
  }

  public void testNextResultOnIterator() {
    doTest("int",
           """
             from typing import Iterable, TypeVar

             T = TypeVar('T')

             xs: Iterable[int]
             expr = iter(xs).__next__()
             """);
  }

  public void testWeakUnionTypeOfOfGenericMethodCallReceiver() {
    doTest("str",
           """
             from typing import Any, Generic, TypeVar

             T = TypeVar("T")

             class Box(Generic[T]):
                 def get(self) -> T:
                     pass

             class StrBox(Box[str]):
                 pass

             receiver: Any | int | StrBox = ...
             expr = receiver.get()""");
  }

  // PY-53105
  public void testWeakUnionTypeOfOfGenericVariadicMethodCallReceiver() {
    doTest("tuple[str, int, float]",
           """
            from typing import Any, Generic, TypeVarTuple, Tuple
                                
            Ts = TypeVarTuple("Ts")
            
            class Box(Generic[*Ts]):
                def get(self) -> Tuple[*Ts]:
                    pass
            
            class StrBox(Box[str, int, float]):
                pass
            
            receiver: Any | int | StrBox = ...
            expr = receiver.get()""");
  }

  public void testGenericClassTypeHintedInDocstrings() {
    doTest("int",
           """
             from typing import Generic, TypeVar

             T = TypeVar('T')

             class User1(Generic[T]):
                 def __init__(self, x: T):
                     self.x = x

                 def get(self) -> T:
                     return self.x

             c = User1(10)
             expr = c.get()""");
  }

  // PY-53105
  public void testGenericVariadicClassTypeHintedInDocstrings() {
    doTest("tuple[int, str, float]",
           """
             from typing import Generic, TypeVar, TypeVarTuple, Tuple
                           
             Ts = TypeVarTuple('Ts')
             
             class User1(Generic[*Ts]):
                 def __init__(self, x: Tuple[*Ts]):
                     self.x = x
             
                 def get(self) -> Tuple[*Ts]:
                     return self.x
             
             c = User1((1, '2', 3.3))
             expr = c.get()""");
  }

  public void testIterOnListOfListsResult() {
    doTest("Iterator[list[int]]",
           "expr = iter([[1, 2, 3]])");
  }

  public void testSwappingTypeParametersInConstructor() {
    doTest("Pair[str, int]",
           """
             from typing import Generic, TypeVar

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')


             class Pair(Generic[T1, T2]):
                 def __init__(self, pair: 'Pair[T2, T1]'):
                     pass


             int_then_str: Pair[int, str] = ...()
             expr = Pair(int_then_str)""");
  }

  public void testDefaultDictFromDict() {
    doTest("defaultdict[Any, dict]",
           "from collections import defaultdict\n" +
           "expr = defaultdict(dict)");
  }

  public void testDecoratorWithArgumentCalledAsFunction() {
    doTest("(str) -> int",
           """
             from typing import Callable, TypeVar

             S = TypeVar('S')
             T = TypeVar('T')

             def dec(t: T):
                 def g(fun: Callable[[], S]) -> Callable[[T], S]:
                     ...

                 return g

             def func() -> int:
                 ...

             expr = dec('foo')(func)""");
  }

  // PY-53105
  public void testGenericVariadicDecoratorWithArgumentCalledAsFunction() {
    doTest("(str, int) -> tuple[int, str, float]",
           """
            from typing import Callable, TypeVar, TypeVarTuple, Tuple
                                    
            Ss = TypeVarTuple('Ss')
            Ts = TypeVarTuple('Ts')
            
            def dec(ts: Tuple[*Ts]):
                def g(fun: Callable[[], Tuple[*Ss]]) -> Callable[[*Ts], Tuple[*Ss]]:
                    ...
            
                return g
            
            def func() -> Tuple[int, str, float]:
                ...
            
            expr = dec(('foo', 42))(func)""");
  }

  public void testGenericParameterOfExpectedCallable() {
    doTest("int",
           """
             from typing import Callable, Generic, TypeVar

             T = TypeVar('T')

             class Super(Generic[T]):
                 pass

             class Sub(Super[T]):
                 pass

             def f(x: Callable[[Sub[T]], None]) -> T:
                 pass

             def g(x: Super[int]):
                 pass

             expr = f(g)""");
  }

  // PY-53105
  public void testGenericVariadicParameterOfExpectedCallable() {
    doTest("tuple[int, str, float]",
           """
            from typing import Callable, Generic, TypeVar, TypeVarTuple, Tuple
                      
            Ts = TypeVarTuple('Ts')
            
            class Super(Generic[*Ts]):
                pass
            
            class Sub(Super[*Ts]):
                pass
            
            def f(x: Callable[[Sub[*Ts]], None]) -> Tuple[*Ts]:
                pass
            
            def g(x: Super[int, str, float]):
                pass
            
            expr = f(g)""");
  }

  // PY-53522
  public void testGenericIteratorParameterizedWithAnotherGeneric() {
    doTest("Entry[str]",
           """
             from typing import Iterator, Generic, TypeVar

             T = TypeVar("T")

             class Entry(Generic[T]):
                 pass

             class MyIterator(Iterator[Entry[T]]):
                 def __next__(self) -> Entry[T]: ...

             def iter_entries(path: T) -> MyIterator[T]: ...

             def main() -> None:
                 for x in iter_entries("some path"):
                     expr = x
             """);
  }

  // PY-53105
  public void testGenericVariadicIteratorParameterizedWithAnotherGenericVariadic() {
    doTest("Entry[str, int, float]",
           """
             from typing import Iterator, Generic, Tuple, TypeVarTuple
                             
             Ts = TypeVarTuple("Ts")
             
             class Entry(Generic[*Ts]):
                 pass
             
             class MyIterator(Iterator[Entry[*Ts]]):
                 def __next__(self) -> Entry[*Ts]: ...
             
             def iter_entries(path: Tuple[*Ts]) -> MyIterator[*Ts]: ...
             
             def main() -> None:
                 for x in iter_entries(("some path", 1, 1.1)):
                     expr = x
             """);
  }

  // PY-53522
  public void testGenericParameterizedWithGeneric() {
    doTest("list[int]",
           """
             from typing import Generic, TypeVar

             T = TypeVar('T')

             class Box(Generic[T]):
                 def get(self) -> T:
                     pass

             class ListBox(Box[list[T]]):
                 pass

             xs: ListBox[int] = ...
             expr = xs.get()
             """);
  }

  // PY-53105
  public void testGenericVariadicParameterizedWithGenericVariadic() {
    doTest("tuple[tuple[int, str]]",
              """
              from typing import Generic, TypeVar, TypeVarTuple, Tuple
                            
              Ts = TypeVarTuple('Ts')
              
              
              class Box(Generic[*Ts]):
                  def get(self) -> Tuple[*Ts]:
                      pass
              
              
              class ListBox(Box[Tuple[*Ts]]):
                  pass
              
              
              xs: ListBox[int, str] = ...
              expr = xs.get()
              """);
  }

  // PY-52656
  public void testClassInheritsGenericToOrderTypeParameters() {
    doTest("str",
           """
             from typing import Generic, TypeVar

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Box(Generic[T1]):
                 def get(self) -> T1:
                     pass

             class Pair(Box[T2], Generic[T1, T2]):
                 pass

             xs: Pair[int, str] = ...
             expr = xs.get()
             """);
  }

  // PY-54336
  public void testReusedTypeVarsAndInheritanceDoNotCauseRecursiveSubstitution() {
    doTest("Sub[T1]",
           """
             from typing import Generic, TypeVar

             T1 = TypeVar('T1')
             T2 = TypeVar('T2')

             class Super(Generic[T1]):
                 pass

             class Sub(Super[T2]):
                 def __init__(self, xs: list[T2]):
                     pass

             def func(xs: list[T1]):
                 expr = Sub(xs)
             """);
  }

  // PY-50542
  public void testReusedTypeVarsInOppositeOrderDoNotCauseRecursiveSubstitution() {
    doTest("str",
           """
             from typing import TypeVar
                          
             T1 = TypeVar('T1')
             T2 = TypeVar('T2')
                          
             def f(x: T1, y: T2) -> T2:
                 pass
                          
             def g(x: T2, y: T1):
                 return f(x, y)
                          
             expr = g(42, 'foo')
             """);
  }

  // PY-56541
  public void testRecursiveTypedDictDeclarations() {
    //RecursionManager.assertOnRecursionPrevention(getTestRootDisposable());
    StringBuilder text = new StringBuilder("""
                                             from __future__ import annotations
                                             from typing import TypedDict, Union
                                             """);
    int typedDictCount = 30;
    for (int i = 1; i <= typedDictCount; i++) {
      text.append(String.format("""
                                  class D%d(TypedDict):
                                      key%d: Alias
                                  """, i, i));
    }
    text.append("Alias = Union[");
    for (int i = 1; i <= typedDictCount; i++) {
      text.append("D").append(i).append(", ");
    }
    text.append("]\n");
    text.append("expr: D1\n");

    myFixture.configureByText(PythonFileType.INSTANCE, text.toString());
    PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    PyType type = codeAnalysis.getType(expr);
    assertInstanceOf(type, PyTypedDictType.class);
    assertTrue(countTypes(type) < 100);
  }

  public static int countTypes(@Nullable PyType type) {
    int result = 1;
    if (type instanceof PyUnionType pyUnionType) {
      for (PyType member : pyUnionType.getMembers()) {
        result += countTypes(member);
      }
    }
    else if (type instanceof PyCollectionType pyCollectionType) {
      for (PyType member : pyCollectionType.getElementTypes()) {
        result += countTypes(member);
      }
    }
    return result;
  }

  // PY-59548
  public void testGenericBaseClassSpecifiedThroughAlias() {
    doTest("int",
           """
             from typing import Generic, TypeVar
             
             T = TypeVar('T')
             
             class Super(Generic[T]):
                 pass
             
             Alias = Super
             
             class Sub(Alias[T]):
                 pass
             
             def f(x: Super[T]) -> T:
                 pass
             
             arg: Sub[int]
             expr = f(arg)
             """);
  }

  // PY-59548
  public void testGenericBaseClassSpecifiedThroughAliasInImportedFile() {
    doMultiFileStubAwareTest("int",
           """
             from typing import TypeVar
             from mod import Sub, Super
             
             T = TypeVar('T')
             
             def f(x: Super[T]) -> T:
                 pass
             
             arg: Sub[int]
             expr = f(arg)
             """);
  }

  public void testTypeOfArgsParameterAnnotatedWithTypeVarTuple() {
    doTest("tuple[*Ts]",
           """
             from typing import TypeVarTuple
             
             Ts = TypeVarTuple("Ts")
             
             def f(*args: *Ts):
                 expr = args
             """);
  }

  public void testTypeOfArgsParameterAnnotatedWithBoundUnpackedTuple() {
    doTest("tuple[int, str]",
           """
             def f(*args: *tuple[int, str]):
                 expr = args
             """);
  }

  public void testTypeOfArgsParameterAnnotatedWithUnboundUnpackedTuple() {
    doTest("tuple[int, ...]",
           """
             def f(*args: *tuple[int, ...]):
                 expr = args
             """);
  }

  // PY-61883
  public void testSimpleGenericClassWithPEP695TypeParameterSyntax() {
    doTest("str", """
      class MyStack[T]:
          def pop(self) -> T:
              pass
          
      stack = MyStack[str]()
      expr = stack.pop()
      """);
  }

  // PY-61883
  public void testSimpleGenericFunWithPEP695Syntax() {
    doTest("int", """
      def foo[T](x: T) -> T:
        return x
      
      expr = foo(1)
      """);
  }

  // PY-61883
  public void testGenericClassParameterizedWithTypeOfConstructorArgumentWithPEP695Syntax() {
    doTest("C[int]", """
      class C[T]:
          def __init__(self, x: T):
              pass
      
      expr = C(10)
      """);
  }

  // PY-61883
  public void testGenericBaseWithPEP695SyntaxClassSpecifiedThroughAlias() {
    doTest("int",
           """
             class Super[T]:
                 pass
        
             Alias = Super

             class Sub[T](Alias[T]):
                 pass
                         
             def f[T](x: Super[T]) -> T:
                 pass
                         
             arg: Sub[int]
             expr = f(arg)
              """);
  }

  // PY-61883
  public void testExplicitlyParametrizedGenericClassInstanceWithPEP695Syntax() {
    doTest("Node[int]",
           """
            from typing import List
            
            class Node[T]:
                def __init__(self, children: List[T]):
                    self.children = children
            
            
            expr = Node[int]()""");
  }

  // PY-61883
  public void testParameterizedWithPEP695SyntaxClassInheritance() {
    doTest("int",
           """
             class B[T]:
                 def foo(self) -> T:
                     pass
        
             class C[T](B[T]):
                 def __init__(self, x: T):
                     pass

             expr = C(10).foo()
             """);
  }

  // PY-61883
  public void testGenericFieldOfClassParameterizedWithNewPEP695Syntax() {
    doTest("str",
           """
             class C[T]:
                 def __init__(self, foo: T):
                     self.foo = foo
                          
             def f() -> C[str]:
                 return C('test')
                          
             x = f()
             expr = x.foo
             """);
  }

  // PY-61883
  public void testMultiTypeExplicitlyParametrizedGenericClassInstanceWithPEP695Syntax() {
    doTest("float",
           """
             class FirstType[T]: pass
             class SecondType[V]: pass
             class ThirdType[Z]: pass
             class Clazz[T, V, Z](FirstType[T], SecondType[V], ThirdType[Z]):
                 first: T
                 second: V
                 third: Z
                          
                 def __init__(self):
                     pass
                          
             node = Clazz[str, int, float]()
             expr = node.third""");
  }

  // PY-61883
  public void testGenericUserFunctionWithManyParamsAndNestedCallWithPEP695Syntax() {
    doTest("tuple[bool, int, str]",
           """
             def myid[T](x: T) -> T:
                 pass
                          
             def f[T, U, V](x: T, y: U, z: V):
                 return myid(x), myid(y), myid(z)
                          
             expr = f(True, 1, 'foo')
             """);
  }

  // PY-61883
  public void testGenericSubstitutionInDeepHierarchyWithPEP695Syntax() {
    doTest("int",
           """
             class Root[T1, T2]:
                 def m(self) -> T2:
                     pass
                          
             class Base3[T1](Root[T1, int]):
                 pass
                          
             class Base2[T1](Base3[T1]):
                 pass
                          
             class Base1[T1](Base2[T1]):
                 pass
                          
             class Sub[T1](Base1[T1]):
                 pass
                          
             expr = Sub().m()
             """);
  }

  // PY-61883
  public void testGenericClassSpecializesInheritedParameterAndAddsNewOneWithPEP695Syntax() {
    doTest("StrBoxWithExtra[int]",
           """
             class Box[T]:
                 pass
                          
             class StrBoxWithExtra[T2](Box[str]):
                 def __init__(self, extra: T2):
                     self.extra = extra
                          
             expr = StrBoxWithExtra(42)""");
  }

  // PY-61883
  public void testSimpleTypeAliasWithPEP695Syntax() {
    doTest("str",
           """
             type myType = str
             def foo() -> myType:
                 pass
             expr = foo()
             """);
  }

  // PY-61883
  public void testGenericTypeAliasForTupleWithPEP695Syntax() {
    doTest("tuple[int, int]",
           """
             type Pair[T] = tuple[T, T]
             expr: Pair[int]""");
  }

  // PY-61883
  public void testGenericTypeAliasParameterizedInTwoStepsWithPEP695Syntax() {
    doTest("dict[int, str]",
           """
             type Alias1[T1, T2] = dict[T1, T2]
             type Alias2[T2] = Alias1[int, T2]
             expr: Alias2[str]""");
  }

  // PY-61883
  public void testGenericClassDefinedInAnotherFileWithPEP695Syntax() {
    doMultiFileStubAwareTest("int",
                             """
                               from a import Stack

                               expr = Stack[int]().pop()""");
  }

  // PY-61883
  public void testRecursiveTypeAliasInAnotherFilePEP695Syntax() {
    doMultiFileStubAwareTest("list[Any] | int",
                             """
                               from a import MyType

                               expr: MyType = ...""");
  }

  // PY-61883
  public void testTrivialRecursiveTypeAliasInAnotherFileWithPEP695Syntax() {
    doMultiFileStubAwareTest("Any",
                             """
                               from a import alias

                               expr: alias""");
  }

  // PY-61883
  public void testGenericTypeAliasInAnotherFileWithPEP695Syntax() {
    doMultiFileStubAwareTest("dict[str, int]",
                             """
                               from a import alias

                               expr: alias[str, int]""");
  }

  // PY-61883
  public void testGenericFunctionInAnotherFileWithPEP695Syntax() {
    doMultiFileStubAwareTest("int",
                             """
                               from a import foo

                               expr = foo(42)""");
  }

  // PY-61883
  public void testGenericProtocolUnificationSameTypeVarWithPEP695Syntax() {
    doTest("list[int]",
           """
             from typing import Protocol
                          
             class SupportsIter[T](Protocol):
                 def __iter__(self) -> T:
                     pass
                          
                          
             def my_iter[T](x: SupportsIter[T]) -> T:
                 pass
                          
                          
             class MyList:
                 def __iter__(self) -> list[int]:
                     pass
                          
                          
             expr = my_iter(MyList())""");
  }

  // PY-61883
  public void testGenericProtocolUnificationSeparateTypeVarWithPEP695Syntax() {
    doTest("list[int]",
           """
             from typing import Protocol

             class SupportsIter[T](Protocol):
                 def __iter__(self) -> T:
                     pass

             def my_iter[T2](x: SupportsIter[T2]) -> T2:
                 pass

             class MyList:
                 def __iter__(self) -> list[int]:
                     pass

             expr = my_iter(MyList())""");
  }

  // PY-61883
  public void testGenericProtocolUnificationGenericImplementationWithGenericSuperclassWithPEP695Syntax() {
    doTest("int",
           """
             from typing import Protocol
                          
                          
             class Fooable[T1](Protocol):
                 def foo(self) -> T1:
                     ...
                          
             class Super[T2]:
                 def foo(self) -> T2:
                     ...
                          
             class MyClass[T2](Super[T2]):
                 pass
                          
             def f[T1](x: Fooable[T1]) -> T1:
                 ...
                          
             obj: MyClass[int]
             expr = f(obj)""");
  }

  // PY-61883
  public void testGenericVariadicByCallableWithPEP695Syntax() {
    doTest("tuple[int, str]",
           """
             from typing import Callable, Tuple
                          
             def foo[*Ts](f: Callable[[*Ts], Tuple[*Ts]]) -> Tuple[*Ts]: ...
                          
             def bar(a: int, b: str) -> Tuple[int, str]: ...
                          
             expr = foo(bar)
             """);
  }

  // PY-61883
  public void testGenericVariadicByCallablePrefixSuffixWithPEP695Syntax() {
    doTest("tuple[str, str, float, int, bool]",
           """
             from typing import Callable, Tuple
                          
             def foo[T, *Ts](f: Callable[[int, *Ts, T], Tuple[T, *Ts]]) -> Tuple[str, *Ts, int, T]: ...
                          
             def bar(a: int, b: str, c: float, d: bool) -> Tuple[bool, str, float]: ...
                          
             expr = foo(bar)
             """);
  }

  // PY-61883
  public void testGenericVariadicClassWithPEP695Syntax() {
    doTest("A[float, bool, list[str]]",
           """
             from typing import Generic, Tuple
                          
             class A[*Ts]:
                 def __init__(self, value: Tuple[int, *Ts]) -> None:
                     self.field: Tuple[int, *Ts] = value
                          
             tpl = (42, 1.1, True, ['42'])
             expr = A(tpl)
             """);
  }

  // PY-61883
  public void testGenericVariadicClassFieldWithPEP695Syntax() {
    doTest("tuple[int, float, bool, list[str]]",
           """
             from typing import Tuple

             class A[*Ts]:
                 def __init__(self, value: Tuple[int, *Ts]) -> None:
                     self.field: Tuple[int, *Ts] = value

             tpl = (42, 1.1, True, ['42'])
             a = A(tpl)
             expr = a.field
             """);
  }

  // PY-61883
  public void testGenericVariadicAndGenericClassWithPEP695Syntax() {
    doTest("A[int | str, int | str, list[int]]",
           """
             from __future__ import annotations
             from typing import Tuple


             class A[T, *Ts, T1]:
                 def __init__(self, t: T, tpl: Tuple[*Ts], t1: T1) -> None:
                     ...

             x: int | str
             expr = A(x, (x,), [1])
             """);
  }

  // PY-61883
  public void testGenericVariadicClassMethodAddAxisPrefixWithPEP695Syntax() {
    doTest("Array[str, int, bool]",
           """
             from __future__ import annotations
             from typing import Tuple, NewType

             class Array[*Shape]:
                 def __init__(self, shape: Tuple[*Shape]):
                     self._shape: Tuple[*Shape] = shape

                 def add_axis_prefix[T](self, t: T) -> Array[T, *Shape]: ...

             shape = (42, True)
             arr: Array[int, bool] = Array(shape)
             expr = arr.add_axis_prefix('')
             """);
  }

  // PY-61883
  public void testGenericVariadicStarArgsAndTypeVarsWithPEP695Syntax() {
    doTest("tuple[str, list[int], bool, int]",
           """
             from typing import TypeVarTuple, Tuple, TypeVar

             def args_to_tuple[T1, T2, *Ts](t1: T1, t2: T2, *args: *Tuple[T2, *Ts, float]) -> Tuple[T2, *Ts, T1]: ...

             expr = args_to_tuple(1, 'a', 'a', [1], True, 3.3)
             """);
  }

  // PY-61883
  public void testTypeAliasStatementTypeNotInterpretedAsAssignedTypeOutsideOfTypeHint() {
    doTest("TypeAliasType",
           """
             type myType = str
             expr = myType
             """);
  }

  // PY-61883
  public void testTypeParameterTypeIsTypingTypeVar() {
    doTest("TypeVar",
           """
             def foo[T]():
                expr = T
             """);
  }

  // PY-61883
  public void testTypeParameterTypeIsTypingParamSpec() {
    doTest("ParamSpec",
           """
             def foo[**P]():
                expr = P
             """);
  }

  // PY-61883
  public void testTypeParameterTypeIsTypingTypeVarTuple() {
    doTest("TypeVarTuple",
           """
             def foo[*Ts]():
                expr = Ts
             """);
  }

  // PY-64481
  public void testIterationOverRegularStrEmitsStrNotLiteralString() {
    doTest("str",
           """
             s = "foo"
             for expr in s:
                 pass
             """);
  }

  // PY-64481
  public void testForLoopTargetTypeComesFromCorrectDunderIterOverload() {
    doTest("Super",
           """
             from typing import overload
                   
             class Super:
                 @overload
                 def __iter__(self: 'Sub') -> list['Sub']: ...
                 @overload
                 def __iter__(self) -> list['Super']: ...
                   
             class Sub(Super): ...
                   
             for expr in Super():
                 pass
             """);
  }

  // PY-36444
  public void testContextManagerDecorator() {
    doTest("str",
           """
             from contextlib import contextmanager

             @contextmanager
             def generator_function():
                 yield "some value"

             with generator_function() as value:
                 expr = value
             """);
  }

  // PY-36444
  public void testTextIOInferredWithContextManagerDecorator() {
    doTest("TextIOWrapper[_WrappedBuffer]",
           """
             from contextlib import contextmanager
                             
             @contextmanager
             def open_file(name: str):
                 f = open(name)
                 yield f
                 f.close()
                             
             cm = open_file(__file__)
             with cm as file:
                 expr = file
             """);
  }

  // PY-71674
  public void testContextManagerDecoratorOnMethod() {
    doTest("str",
           """
             from contextlib import contextmanager
             from typing import Iterator
             
             
             class MyClass:
                 @contextmanager
                 def as_context(self) -> Iterator[str]:
                     yield "foo"
             
             
             with MyClass().as_context() as value:
                 expr = value
             """);
  }

  // PY-70484
  public void testUnboundParamSpecFromUnresolvedArgumentReplacedWithArgsKwargs() {
    doTest("(args: Any, kwargs: Any) -> str",
           """
             from typing import Callable, Any, ParamSpec
                          
             P = ParamSpec("P")
                          
             def deco(fn: Callable[P, Any]) -> Callable[P, str]:
                 return ...
                          
             expr = deco(unresolved)
             """);
  }

  // PY-70484
  public void testUnboundParamSpecThatCannotBeBoundThroughParametersLeftIntact() {
    doTest("((**P) -> Any) -> (**P) -> int",
           """
             from typing import Callable, Any, ParamSpec
                          
             P = ParamSpec("P")
                          
             def deco() -> Callable[[Callable[P, Any]], Callable[P, int]]
                 return ...
                          
             expr = deco()
             """);
  }

  public void testMixingUpConcatenateAndTypeVarTuple() {
    doTest("(int, int, x: int, y: str) -> int",
           """
             from typing import TypeVarTuple, ParamSpec, Callable, Any, Concatenate, reveal_type
                          
             Ts = TypeVarTuple('Ts')
             P = ParamSpec('P')
                          
                          
             def f(prefix: tuple[*Ts], fn: Callable[P, Any]) -> Callable[Concatenate[*Ts, P], int]:
                 ...
                          
             def g(x: int, y: str) -> bool:
                 ...
                          
             expr = f((1, 2), g)
             """);
  }

  public void testParamSpecInConcatenateMappedToAnotherParamSpec() {
    doTest("(Concatenate(int, **P1)) -> Any",
           """
             from typing import Callable, Any, ParamSpec, Concatenate
             
             P1 = ParamSpec('P1')
             P2 = ParamSpec('P2')
             
             def f(fn: Callable[P1, Any]):
                 expr = g(fn)
             
             def g(fn: Callable[P2, Any]) -> Callable[Concatenate[int, P2], Any]:
                ...
             """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassReference() {
    doTest("type[slice[int, int, int | None]]", """
      from typing import TypeVar, Generic
      StartT = TypeVar("StartT", default=int)
      StopT = TypeVar("StopT", default=StartT)
      StepT = TypeVar("StepT", default=int | None)
      class slice(Generic[StartT, StopT, StepT]): ...
      expr = slice
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassReferenceNewSyntax() {
    doTest("type[slice[int, int, int | None]]", """
      class slice[StartT = int, StopT = StartT, StepT = int | None]: ...
      expr = slice
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassCall() {
    doTest("slice[int, int, int | None]", """
      from typing import TypeVar, Generic
      StartT = TypeVar("StartT", default=int)
      StopT = TypeVar("StopT", default=StartT)
      StepT = TypeVar("StepT", default=int | None)
      class slice(Generic[StartT, StopT, StepT]): ...
      expr = slice()
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassCallNewSyntax() {
    doTest("slice[int, int, int | None]", """
      class slice[StartT = int, StopT = StartT, StepT = int | None]: ...
      expr = slice()
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassCallParameterizedWithOneType() {
    doTest("slice[str, str, int | None]", """
      from typing import TypeVar, Generic
      StartT = TypeVar("StartT", default=int)
      StopT = TypeVar("StopT", default=StartT)
      StepT = TypeVar("StepT", default=int | None)
      class slice(Generic[StartT, StopT, StepT]): ...
      expr = slice[str]()
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassCallParameterizedWithOneTypeNewSyntax() {
    doTest("slice[str, str, int | None]", """
      class slice[StartT = int, StopT = StartT, StepT = int | None]: ...
      expr = slice[str]()
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassCallFullyParameterized() {
    doTest("slice[str, bool, complex]", """
      from typing import TypeVar, Generic
      StartT = TypeVar("StartT", default=int)
      StopT = TypeVar("StopT", default=StartT)
      StepT = TypeVar("StepT", default=int | None)
      class slice(Generic[StartT, StopT, StepT]): ...
      expr = slice[str, bool, complex]()
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassCallFullyParameterizedNewSyntax() {
    doTest("slice[str, bool, complex]", """
      class slice[StartT = int, StopT = StartT, StepT = int | None]: ...
      expr = slice[str, bool, complex]()
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsListDefault() {
    doTest("type[Bar[int, list[int]]]", """
      from typing import TypeVar, Generic
      
      T = TypeVar("T")
      ListDefaultT = TypeVar("ListDefaultT", default=list[T])
      
      class Bar(Generic[T, ListDefaultT]):
          def __init__(self, x: T, y: ListDefaultT): ...
      
      expr = Bar[int]
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassWithInitMethodReference() {
    doTest("type[Bar[Any, list[Any]]]", """
      from typing import TypeVar, Generic
      Z1 = TypeVar("Z1")
      ListDefaultT = TypeVar("ListDefaultT", default=list[Z1])
      class Bar(Generic[Z1, ListDefaultT]):
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassWithInitMethodReferenceNewSyntax() {
    doTest("type[Bar[Any, list[Any]]]", """
      from typing import TypeVar, Generic
      class Bar[Z1, ListDefaultT = list[Z1]]:
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassWithInitMethodReferenceParameterizedWithOneType() {
    doTest("type[Bar[int, list[int]]]", """
      from typing import TypeVar, Generic
      Z1 = TypeVar("Z1")
      ListDefaultT = TypeVar("ListDefaultT", default=list[Z1])
      class Bar(Generic[Z1, ListDefaultT]):
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar[int]
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassWithInitMethodCallParameterizedWithOneTypeAndConstructorArguments() {
    doTest("Bar[int, list[int]]", """
      from typing import TypeVar, Generic
      Z1 = TypeVar("Z1")
      ListDefaultT = TypeVar("ListDefaultT", default=list[Z1])
      class Bar(Generic[Z1, ListDefaultT]):
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar[int](0, [])
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassWithInitMethodCallParameterizedWithTwoTypesAndConstructorArguments() {
    doTest("Bar[int, list[str]]", """
      from typing import TypeVar, Generic
      Z1 = TypeVar("Z1")
      ListDefaultT = TypeVar("ListDefaultT", default=list[Z1])
      class Bar(Generic[Z1, ListDefaultT]):
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar[int, list[str]](0, [])
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsClassWithInitMethodCallParameterizedWithTwoTypesAndConstructorArgumentsChangingDefaultType() {
    doTest("Bar[int, str]", """
      from typing import TypeVar, Generic
      Z1 = TypeVar("Z1")
      ListDefaultT = TypeVar("ListDefaultT", default=list[Z1])
      class Bar(Generic[Z1, ListDefaultT]):
          def __init__(self, x: Z1, y: ListDefaultT): ...
      expr = Bar[int, str](0, "")
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsSubclassedClassReference() {
    doTest("type[Bar[str]]", """
      from typing import TypeVar, Generic, TypeAlias
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SubclassMe(Generic[T1, DefaultStrT]):
          x: DefaultStrT
      class Bar(SubclassMe[int, DefaultStrT]): ...
      expr = Bar
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsSubclassedClassInstance() {
    doTest("Bar[str]", """
      from typing import TypeVar, Generic, TypeAlias
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SubclassMe(Generic[T1, DefaultStrT]):
          x: DefaultStrT
      class Bar(SubclassMe[int, DefaultStrT]): ...
      expr = Bar()
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsSubclassedParameterizedClassInstance() {
    doTest("Bar[bool]", """
      from typing import TypeVar, Generic, TypeAlias
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SubclassMe(Generic[T1, DefaultStrT]):
          x: DefaultStrT
      class Bar(SubclassMe[int, DefaultStrT]): ...
      expr = Bar[bool]()
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsSubclassedWithClassAttribute() {
    doTest("str", """
      from typing import TypeVar, Generic, TypeAlias
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SubclassMe(Generic[T1, DefaultStrT]):
          x: DefaultStrT
      class Foo(SubclassMe[float]): ...
      expr = Foo().x
      """);
  }

  // PY-71002
  public void testReferenceToTypeVarTupleWithDefaultIsParameterizedType() {
    doTest("type[Foo[str, int]]", """
      from typing import Generic, TypeVarTuple, Unpack
      DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[str, int]])
      class Foo(Generic[*DefaultTs]): ...
      expr = Foo
      """);
  }

  // PY-71002
  public void testTypeVarTupleWithDefault() {
    doTest("Foo[str, int]", """
      from typing import Generic, TypeVarTuple, Unpack
      DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[str, int]])
      class Foo(Generic[*DefaultTs]): ...
      expr = Foo()
      """);
  }

  // PY-71002
  public void testTypeVarTupleWithDefaultsClassInstanceNewSyntax() {
    doTest("Foo[str, int]", """
      from typing import Unpack
      class Foo[*DefaultTs = Unpack[tuple[str, int]]]: ...
      expr = Foo()
      """);
  }

  // PY-71002
  public void testTypeVarTupleWithDefaultOverridenByExplicit() {
    doTest("Foo[bool, float]", """
      from typing import Generic, TypeVarTuple, Unpack
      DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[str, int]])
      class Foo(Generic[*DefaultTs]): ...
      expr = Foo[bool, float]()
      """);
  }

  // PY-71002
  public void testTypeVarTupleWithDefaultParameterizedWithAnotherGeneric() {
    doTest("Foo[list, list, int]", """
      from typing import Generic, TypeVarTuple, Unpack, TypeVar
      T = TypeVar("T", default=list)
      DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[T, int]])
      class Foo(Generic[T, *DefaultTs]): ...
      expr = Foo()
      """);
  }

  // PY-71002
  public void testParamSpecWithDefaultsClassReference() {
    doTest("type[Foo[[str, int]]]", """
      from typing import ParamSpec, Generic
      DefaultP = ParamSpec("DefaultP", default=[str, int])
      class Foo(Generic[DefaultP]): ...
      expr = Foo
      """);
  }

  // PY-71002
  public void testParamSpecWithDefaultsClassReferencesNewSyntax() {
    doTest("type[Foo[[str, int]]]", """
      class Foo[**P = [str, int]]: ...
      expr = Foo
      """);
  }

  // PY-71002
  public void testParamSpecWithDefaultsClassInstance() {
    doTest("Foo[[str, int]]", """
      from typing import ParamSpec, Generic
      DefaultP = ParamSpec("DefaultP", default=[str, int])
      class Foo(Generic[DefaultP]): ...
      expr = Foo()
      """);
  }

  // PY-71002
  public void testParamSpecWithEmptyDefaultsClassInstance() {
    doTest("Foo[[]]", """
      from typing import ParamSpec, Generic
      DefaultP = ParamSpec("DefaultP", default=[])
      class Foo(Generic[DefaultP]): ...
      expr = Foo()
      """);
  }

  // PY-71002
  public void testParamSpecWithDefaultsClassInstanceNewSyntax() {
    doTest("Foo[[str, int]]", """
      class Foo[**P = [str, int]]: ...
      expr = Foo()
      """);
  }

  // PY-71002
  public void testParamSpecWithEmptyDefaultsClassInstanceNewSyntax() {
    doTest("Foo[[]]", """
      class Foo[**P = []]: ...
      expr = Foo()
      """);
  }

  // PY-71002
  public void testParamSpecWithDefaultsParameterizedClassInstance() {
    doTest("Foo[[int, bool]]", """
      from typing import ParamSpec, Generic
      DefaultP = ParamSpec("DefaultP", default=[str, int])
      class Foo(Generic[DefaultP]): ...
      expr = Foo[[int, bool]]()
      """);
  }

  // PY-71002
  public void testNewStyleGenericFunctionWithDefault() {
    doTest("int", """
      def foo[T = int](x: T = None) -> T: ...
      expr = foo()
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsEmptyConstructorCall() {
    doTest("Box[int, str, bool]", """
      from typing import TypeVar, Generic
      T = TypeVar("T", default=int)
      T1 = TypeVar("T1", default=str)
      T2 = TypeVar("T2", default=bool)
      class Box(Generic[T, T1, T2]):
          def __init__(self, a: T = None, b: T1 = None, c: T2 = None):
              self.value = a
              self.value1 = b
              self.value2 = c
      expr = Box()
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsDefaultOverridenByExplicitConstructorArgument() {
    doTest("Box[str, str, bool]", """
      from typing import TypeVar, Generic
      T = TypeVar("T", default=int)
      T1 = TypeVar("T1", default=str)
      T2 = TypeVar("T2", default=bool)
      class Box(Generic[T, T1, T2]):
          def __init__(self, a: T = None, b: T1 = None, c: T2 = None):
              self.value = a
              self.value1 = b
              self.value2 = c
      expr = Box("str")
      """);
  }

  // PY-71002
  public void testTypeVarWithDefaultsMethodReturnType() {
    doTest("int", """
      from typing import TypeVar, Generic
      DefaultIntT = TypeVar('DefaultIntT', default=int)
      class Test(Generic[DefaultIntT]):
          def foo(self) -> DefaultIntT: ...
      expr = Test().foo()
      """);
  }

  // PY-71002
  public void testParamSpecWithDefaultsExtendedCase() {
    doTest("(float, bool) -> int | None", """
      from typing import Callable, TypeVar, Optional, ParamSpec
      T = TypeVar('T', default=int)
      P = ParamSpec('P', default=[float, bool])
      def catch_exception(function: Callable[P, T]) -> Callable[P, Optional[T]]:
          def decorator(*args: P.args, **kwargs: P.kwargs) -> Optional[T]:...
          return decorator
      expr = catch_exception()
      """);
  }

  // PY-71002
  public void testParamSpecWithEmptyDefaultsExtendedCase() {
    doTest("() -> int | None", """
      from typing import Callable, TypeVar, Optional, ParamSpec
      T = TypeVar('T', default=int)
      P = ParamSpec('P', default=[])
      def catch_exception(function: Callable[P, T]) -> Callable[P, Optional[T]]:
          def decorator(*args: P.args, **kwargs: P.kwargs) -> Optional[T]:...
          return decorator
      expr = catch_exception()
      """);
  }

  // PY-71002
  public void testParamSpecWithDefaultsExtendedCaseDefaultsOverridden() {
    doTest("(a: str, b: int, c: list[float]) -> float | None", """
      from typing import Callable, TypeVar, Optional
      from typing_extensions import ParamSpec  # or `typing` for `python>=3.10`
      T = TypeVar('T', default=int)
      P = ParamSpec('P', default=[float, bool])
      def catch_exception(function: Callable[P, T]) -> Callable[P, Optional[T]]:
          def decorator(*args: P.args, **kwargs: P.kwargs) -> Optional[T]:...
          return decorator
      def some_func(a: str, b: int, c: list[float]) -> float: ...
      expr = catch_exception(some_func)
      """);
  }

  // PY-71002
  public void testTypeVarWithDefaultsClassVariable() {
    doTest("int", """
      from typing import TypeVar, Generic
      DefaultIntT = TypeVar('DefaultIntT', default=int)
      class Test(Generic[DefaultIntT]):
          x: DefaultIntT
      expr = Test().x
      """);
  }

  // PY-71002
  public void testTypeVarWithDefaultsClassVariableOverriden() {
    doTest("str", """
      from typing import TypeVar, Generic
      DefaultIntT = TypeVar('DefaultIntT', default=int)
      class Test(Generic[DefaultIntT]):
          x: DefaultIntT
      expr = Test[str]().x
      """);
  }

  // PY-71002
  public void testTypeVarWithDefaultsClassVariableNewSyntax() {
    doTest("int", """
      class Test[DefaultIntT = int]:
          x: DefaultIntT
      expr = Test().x
      """);
  }

  // PY-71002
  public void testTypeVarWithDefaultClassVariableTypeDefinedViaAnotherTypeVarWithNewSyntax() {
    doTest("int", """
      class Test[T = int, U = T]:
          x: U
      
      expr = Test().x
      """);
  }

  // PY-71002
  public void testTypeVarWithDefaultsClassVariableOverridenNewSyntax() {
    doTest("str", """
      class Test[DefaultIntT = int]:
          x: DefaultIntT
      expr = Test[str]().x
      """);
  }

  // PY-71002
  public void testTypeVarWithDefaultsClassVariableAccessViaReference() {
    doTest("int", """
      from typing import TypeVar, Generic
      DefaultIntT = TypeVar('DefaultIntT', default=int)
      class Test(Generic[DefaultIntT]):
          x: DefaultIntT
      expr = Test.x
      """);
  }

  // PY-71002
  public void testMixedTypeVarsWithDefaultsAndNonDefaults() {
    doTest("AllTheDefaults[int, complex, str, int, bool]", """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      DefaultIntT = TypeVar("DefaultIntT", default=int)
      DefaultBoolT = TypeVar("DefaultBoolT", default=bool)
      class AllTheDefaults(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]): ...
      expr = AllTheDefaults[int, complex]()
      """);
  }

  // PY-71002
  public void testMixedTypeVarsWithDefaultsAndNonDefaultsReferenceType() {
    doTest("type[AllTheDefaults[int, complex, str, int, bool]]", """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      DefaultIntT = TypeVar("DefaultIntT", default=int)
      DefaultBoolT = TypeVar("DefaultBoolT", default=bool)
      class AllTheDefaults(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]): ...
      expr = AllTheDefaults[int, complex]
      """);
  }

  // PY-71002
  public void testMixedTypeVarsWithDefaultsAndNonDefaultsOneTypeParamMissing() {
    doTest("AllTheDefaults[int, Any, str, int, bool]", """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      DefaultIntT = TypeVar("DefaultIntT", default=int)
      DefaultBoolT = TypeVar("DefaultBoolT", default=bool)
      class AllTheDefaults(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]): ...
      expr = AllTheDefaults[int]()
      """);
  }

  // PY-71002
  public void testMixedTypeVarsWithDefaultsAndNonDefaultsTwoTypeParamsMissing() {
    doTest("AllTheDefaults[Any, Any, str, int, bool]", """
      from typing import TypeVar, Generic
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      DefaultIntT = TypeVar("DefaultIntT", default=int)
      DefaultBoolT = TypeVar("DefaultBoolT", default=bool)
      class AllTheDefaults(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]): ...
      expr = AllTheDefaults()
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsLongTypeVarToTypeVarChain() {
    doTest("str", """
      from typing import TypeVar, Generic
      
      T = TypeVar("T", default=str)
      T1 = TypeVar("T1", default=T)
      T2 = TypeVar("T2", default=T1)
      T3 = TypeVar("T3", default=T2)
      T4 = TypeVar("T4", default=T3)
      T5 = TypeVar("T5", default=T4)
      T6 = TypeVar("T6", default=T5)
      T7 = TypeVar("T7", default=T6)
      T8 = TypeVar("T8", default=T7)
      
      class Box(Generic[T, T1, T2, T3, T4, T5, T6, T7, T8]):
          value: T8
      expr = Box().value
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsLongTypeVarToTypeVarChainWithFirstOverriden() {
    doTest("list", """
      from typing import TypeVar, Generic
      
      T = TypeVar("T", default=str)
      T1 = TypeVar("T1", default=T)
      T2 = TypeVar("T2", default=T1)
      T3 = TypeVar("T3", default=T2)
      T4 = TypeVar("T4", default=T3)
      T5 = TypeVar("T5", default=T4)
      T6 = TypeVar("T6", default=T5)
      T7 = TypeVar("T7", default=T6)
      T8 = TypeVar("T8", default=T7)
      
      class Box(Generic[T, T1, T2, T3, T4, T5, T6, T7, T8]):
          value: T8
      expr = Box[list]().value
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsLongTypeVarToTypeVarChainWithFirstAndSecondOverriden() {
    doTest("int", """
      from typing import TypeVar, Generic
      
      T = TypeVar("T", default=str)
      T1 = TypeVar("T1", default=T)
      T2 = TypeVar("T2", default=T1)
      T3 = TypeVar("T3", default=T2)
      T4 = TypeVar("T4", default=T3)
      T5 = TypeVar("T5", default=T4)
      T6 = TypeVar("T6", default=T5)
      T7 = TypeVar("T7", default=T6)
      T8 = TypeVar("T8", default=T7)
      
      class Box(Generic[T, T1, T2, T3, T4, T5, T6, T7, T8]):
          value: T8
      expr = Box[list, int]().value
      """);
  }

  // PY-71002
  public void testTypeVarDefaultsLongTypeVarToTypeVarChainWithMultipleDefaults() {
    doTest("str | bool | float", """
      from typing import TypeVar, Generic
      
      T = TypeVar("T", default=str)
      T1 = TypeVar("T1", default=T)
      T2 = TypeVar("T2", default=float)
      T3 = TypeVar("T3", default=T2)
      T4 = TypeVar("T4", default=bool)
      T5 = TypeVar("T5", default=T4)
      T6 = TypeVar("T6", default=T5)
      T7 = TypeVar("T7", default=T6)
      T8 = TypeVar("T8", default=T7)
      
      class Box(Generic[T, T1, T2, T3, T4, T5, T6, T7, T8]):
          value: T | T8 | T3 = None
      
      expr = Box().value
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithDefaults() {
    doTest("dict[int, str]", """
      type Alias[T = int, U = str] = dict[T, U]
      expr: Alias
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithDefaultsOneOverriden() {
    doTest("dict[bool, str]", """
      type Alias[T = int, U = str] = dict[T, U]
      expr: Alias[bool]
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithDefaultsTwoOverriden() {
    doTest("dict[bool, list[int]]", """
      type Alias[T = int, U = str] = dict[T, list[U]]
      x: Alias[bool, int]
      expr = x
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithDefaultsNested() {
    doTest("dict[int, list[str]]", """
      type Alias[T = int, U = str] = dict[T, list[U]]
      expr: Alias
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithDefaultsNestedOneOverriden() {
    doTest("dict[bool, list[str]]", """
      type Alias[T = int, U = str] = dict[T, list[U]]
      expr: Alias[bool]
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithDefaultsNestedTwoOverriden() {
    doTest("dict[bool, list[bool]]", """
      type Alias[T = int, U = str] = dict[T, list[U]]
      expr: Alias[bool, bool]
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithTypeVarOnly() {
    doTest("int", """
      type Alias[T = int] = T
      expr: Alias
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithTypeVarOnlyOverriden() {
    doTest("bool", """
      type Alias[T = int] = T
      expr: Alias[bool]
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithDefaultsUnion() {
    doTest("int | str", """
      type Alias[T = int, U = str] = T | U
      expr: Alias
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithDefaultsOneTypeOfUnionOverriden() {
    doTest("bool | str", """
      type Alias[T = int, U = str] = T | U
      expr: Alias[bool]
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithDefaultsUnionOverriden() {
    doTest("bool | float", """
      type Alias[T = int, U = str] = T | U
      expr: Alias[bool, float]
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasUnionChangedOrder() {
    doTest("str | list[int]", """
      type Alias[T = int, U = str] = U | list[T]
      expr: Alias
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasUnionChangedOrderDefaultsOverriden() {
    doTest("bool | list[float]", """
      type Alias[T = int, U = str] = U | list[T]
      expr: Alias[float, bool]
      """);
  }

  // PY-71002
  public void testTypeAliasOneWithoutDefault() {
    doTest("dict[Any, str] | list[float]", """
      from typing import TypeVar, TypeAlias
      T = TypeVar("T")
      U = TypeVar("U", default=str)
      B = TypeVar("B", default=float)
      Alias : TypeAlias = dict[T, U] | list[B]
      expr: Alias
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasOneWithoutDefault() {
    doTest("dict[Any, str] | list[float]", """
      type Alias[T, U = str, B = float] = dict[T, U] | list[B]
      expr: Alias
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasOneWithoutDefaultParameterized() {
    doTest("dict[int, str] | list[float] | int", """
      type Alias[T, U = str, B = float] = dict[T, U] | list[B] | T
      expr: Alias[int]
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasOneWithoutDefaultAllOverriden() {
    doTest("dict[int, int] | list[int] | int", """
      type Alias[T, U = str, B = float] = dict[T, U] | list[B] | T
      expr: Alias[int, int, int]
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithDefaultsPrevTypeVarAsDefault() {
    doTest("str | list[str]", """
      type Alias[T, U = T] = T | list[U]
      expr: Alias[str]
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithDefaultsTypeVarChain() {
    doTest("str", """
      type Alias[T = str, T1 = T, T2 = T1, T3 = T2, T4 = T3] = T4
      expr: Alias
      """);
  }

  // PY-71002
  public void testParamSpecWithDefaultsDefinedInTypeAlias() {
    doTest("(func: (str, int) -> str) -> None", """
      from typing import ParamSpec, Generic, Callable
      type PAlias[T = str, **P = [str, int]] = Callable[P, T]
      def wrapper(func: PAlias) -> None:
          pass
      expr = wrapper
      """);
  }

  // PY-71002
  public void testParamSpecWithDefaultsDefinedInTypeAliasOverriden() {
    doTest("(func: (str, str) -> bool) -> None", """
      from typing import ParamSpec, Generic, Callable
      type PAlias[T = str, **P = [str, int]] = Callable[P, T]
      def wrapper(func: PAlias[bool, [str, str]]) -> None:
          pass
      expr = wrapper
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithAllDefaultTypes() {
    doTest("(str, int, str, str) -> float", """
      from typing import Generic, TypeVarTuple, Unpack, ParamSpec, Callable, Any, Concatenate
      type ReturnTupleAlias[T = float, **P = [str, str], *Ts = Unpack[tuple[str, int]]] = Callable[Concatenate[*Ts, P], T]
      def f() -> ReturnTupleAlias: ...
      expr = f()
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithAllDefaultTypesReturnTypeOverriden() {
    doTest("(str, int, str, str) -> list[str]", """
      from typing import Generic, TypeVarTuple, Unpack, ParamSpec, Callable, Any, Concatenate
      type ReturnTupleAlias[T = float, **P = [str, str], *Ts = Unpack[tuple[str, int]]] = Callable[Concatenate[*Ts, P], T]
      def f() -> ReturnTupleAlias[list[str]]: ...
      expr = f()
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithAllDefaultTypesReturnAndParamSpecTypeOverriden() {
    doTest("(str, int, float, float) -> list[str]", """
      from typing import Generic, TypeVarTuple, Unpack, ParamSpec, Callable, Any, Concatenate
      type ReturnTupleAlias[T = float, **P = [str, str], *Ts = Unpack[tuple[str, int]]] = Callable[Concatenate[*Ts, P], T]
      def f() -> ReturnTupleAlias[list[str], [float, float]]: ...
      expr = f()
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithAllDefaultTypesAllOverridden() {
    doTest("(str, float, bool, list[bool], float, float) -> list[str]", """
      from typing import Generic, TypeVarTuple, Unpack, ParamSpec, Callable, Any, Concatenate
      type ReturnTupleAlias[T = float, **P = [str, str], *Ts = Unpack[tuple[str, int]]] = Callable[Concatenate[*Ts, P], T]
      def f() -> ReturnTupleAlias[list[str], [float, float], str, float, bool, list[bool]]: ...
      expr = f()
      """);
  }

  // PY-71002
  public void testTypeVarWithDefaultClassMethodTypeDefinedViaAnotherTypeVarWithNewSyntax() {
    doTest("int", """
      class Test[T = int, U = T]:
          def foo(self) -> U: ...
      expr = Test().foo()
      """);
  }

  // PY-71002
  public void testTypeAliasWithDefaults() {
    doTest("SomethingWithNoDefaults[int, str]", """
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T')
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SomethingWithNoDefaults(Generic[T, T2]): ...
      MyAlias: TypeAlias = SomethingWithNoDefaults[int, DefaultStrT]
      expr: MyAlias
      """);
  }

  // PY-71002
  public void testTypeAliasWithParameterizedInstance() {
    doTest("SomethingWithNoDefaults[int, bool]", """
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T')
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      class SomethingWithNoDefaults(Generic[T, T2]): ...
      MyAlias: TypeAlias = SomethingWithNoDefaults[int, DefaultStrT]
      expr: MyAlias[bool]
      """);
  }

  // PY-71002
  public void testTypeAliasWithDefaultsTuple() {
    doTest("int | str", """
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T', default=int)
      U = TypeVar('U', default=str)
      MyAlias: TypeAlias = T | U
      expr: MyAlias
      """);
  }

  // PY-71002
  public void testTypeAliasWithDefaultsDict() {
    doTest("dict[int, str]", """
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T', default=int)
      U = TypeVar('U', default=str)
      MyAlias: TypeAlias = dict[T, U]
      expr: MyAlias
      """);
  }

  // PY-71002
  public void testTypeAliasWithDefaultsDictOneDefaultOverriden() {
    doTest("dict[str, str]", """
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T', default=int)
      U = TypeVar('U', default=str)
      MyAlias: TypeAlias = dict[T, U]
      expr: MyAlias[str]
      """);
  }

  // PY-71002
  public void testTypeAliasWithDefaultsDictTwoDefaultsOverriden() {
    doTest("dict[str, float]", """
      from typing import Generic, TypeAlias, TypeVar
      T = TypeVar('T', default=int)
      U = TypeVar('U', default=str)
      MyAlias: TypeAlias = dict[T, U]
      expr: MyAlias[str, float]
      """);
  }

  // PY-71002
  public void testTypeAliasWithAllDefaultTypes() {
    doTest("(str, int, str, str) -> float", """
      from typing import Generic, TypeVarTuple, Unpack, ParamSpec, Callable, Any, Concatenate, TypeAlias, TypeVar
      T = TypeVar('T', default = float)
      P = ParamSpec('P', default=[str ,str])
      Ts = TypeVarTuple('Ts', default=Unpack[tuple[str, int]])
      ReturnTupleAlias: TypeAlias = Callable[Concatenate[*Ts, P], T]
      def g() -> ReturnTupleAlias: ...
      expr = g()
      """);
  }

  // PY-71002
  public void testTypeAliasWithParameterizedNonDefaultInDeclaration() {
    doTest("dict[str, int] | str", """
      from typing import TypeVar, Generic, TypeAlias
      T = TypeVar('T')
      T1 = TypeVar("T1")
      T2 = TypeVar("T2")
      DefaultStrT = TypeVar("DefaultStrT", default=str)
      DefaultIntT = TypeVar("DefaultIntT", default=int)
      DefaultBoolT = TypeVar("DefaultBoolT", default=bool)
      class Triple(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]):
          val: dict[T1, T2] | DefaultStrT
      Alias: TypeAlias = Triple[str, int]
      e: Alias = None
      expr = e.val
      """);
  }

  // PY-71002
  public void testClassWithDefaultGenericsDefinedInAnotherFile() {
    doMultiFileStubAwareTest("int", """
      from mod import StackOfIntsByDefault
      stack = StackOfIntsByDefault()
      expr = stack.pop()
      """);
  }

  // PY-71002
  public void testClassWithDefaultGenericsDefinedInAnotherFileDefaultOverriden() {
    doMultiFileStubAwareTest("str", """
      from mod import StackOfIntsByDefault
      stack = StackOfIntsByDefault[str]()
      expr = stack.pop()
      """);
  }

  // PY-71002
  public void testClassWithDefaultGenericsDefinedInAnotherFileAttributeAccess() {
    doMultiFileStubAwareTest("int | str", """
      from mod import Box
      expr = Box.val
      """);
  }

  // PY-71002
  public void testClassWithNewStyleDefaultGenericsDefinedInAnotherFile() {
    doMultiFileStubAwareTest("int", """
      from mod import StackOfIntsByDefault
      stack = StackOfIntsByDefault()
      expr = stack.pop()
      """);
  }

  // PY-71002
  public void testTypeAliasWithDefaultsDefinedInAnotherFile() {
    doMultiFileStubAwareTest("dict[int, str]", """
      from mod import StrIntDict
      expr: StrIntDict
      """);
  }

  // PY-71002
  public void testTypeAliasWithDefaultsDefinedInAnotherFileAliasingGenericClass() {
    doMultiFileStubAwareTest("SomethingWithNoDefaults[int, bool]", """
      from mod import MyAlias
      expr = MyAlias[bool]()
      """);
  }

  // PY-71002
  public void testExplicitAnyNotSubstitutedByDefaults() {
    doTest("Test[Any, Any, bool]", """
      class Test[T = str, T1 = int, T2 = bool]: ...
      expr = Test[Any, Any]()
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasParameterizedInMultipleSteps() {
    doTest("float | int | str", """
      from typing import Union
      type A[T1, T2, T3 = str] = Union[T1, T2, T3]
      type B[T1, T2 = int] = A[T1, T2]
      type C[T1] = B[T1]
      type D = C[float]
      expr: D
      """);
  }

  // PY-71002
  public void testMixedOldAndNewStyleTypeAliasesParameterizedInMultipleSteps() {
    doTest("float | int | str", """
      from typing import Union, TypeVar, TypeAlias
      T = TypeVar("T")
      T1 = TypeVar("T1", default=int)
      type A[T1, T2, T3 = str] = Union[T1, T2, T3]
      B: TypeAlias = A[T, T1]
      C: TypeAlias = B[T]
      type D = C[float]
      expr: D
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithAssignedSubscriptionExpression() {
    doTest("dict[int, str]", """
      type my_dict[K = float, V = bool] = dict[K, V]
      type myIntStrDict = my_dict[int, str]
      expr: myIntStrDict
      """);
  }

  // PY-71002
  public void testNewStyleTypeAliasWithAssignedSubscriptionExpressionAliasingUnion() {
    doTest("bool | list[float]", """
      type Alias[T = int, U = str] = U | list[T]
      type Alias2 = Alias[float, bool]
      expr: Alias2
      """);
  }

  // PY-71002
  public void testOldStyleTypeAliasWithAssignedSubscriptionExpressionAliasingUnion() {
    doTest("float | list[bool]", """
      from typing import TypeVar, Generic, TypeAlias
      T = TypeVar("T", default=int)
      U = TypeVar("U", default=str)
      Alias: TypeAlias = U | list[T]
      Alias2: TypeAlias = Alias[float, bool]
      expr: Alias2
      """);
  }

  // PY-71002
  public void testParamSpecDefaultTypeRefersToAnotherParamSpecNewStyle() {
    doTest("Clazz[[str], [str], [str]]", """
      class Clazz[**P1, **P2 = P1, **P3 = P2]: ...
      expr = Clazz[[str]]()
      """);
  }

  // PY-71002
  public void testParamSpecDefaultTypeRefersToAnotherParamSpecOldStyle() {
    doTest("Clazz[[str], [str], [str]]", """
      from typing import Generic, ParamSpec
      P1 = ParamSpec("P1")
      P2 = ParamSpec("P2", default=P1)
      P3 = ParamSpec("P3", default=P2)
      class Clazz(Generic[P1, P2, P3]): ...
      expr = Clazz[[str]]()
      """);
  }

  // PY-71002
  public void testParamSpecDefaultTypeRefersToAnotherParamSpecOldStyleNoExplicit() {
    doTest("Clazz[[str], [str], [bool, bool], [bool, bool]]", """
      from typing import Generic, ParamSpec
      P1 = ParamSpec("P1", default=[str])
      P2 = ParamSpec("P2", default=P1)
      P3 = ParamSpec("P3", default=[bool, bool])
      P4 = ParamSpec("P4", default=P3)
      class Clazz(Generic[P1, P2, P3, P4]): ...
      expr = Clazz()
      """);
  }

  // PY-71002
  public void testParamSpecWithDefaultInConstructor() {
    doTest("(int, str, str) -> None | None", """
      from typing import Generic, ParamSpec, Callable
      P = ParamSpec("P", default=[int, str, str])
      class ClassA(Generic[P]):
          def __init__(self, x: Callable[P, None] = None) -> None:
              self.x = x
              ...
      expr = ClassA().x
      """);
  }

  // PY-71002
  public void testParamSpecDefaultTypeRefersToAnotherParamSpecWithEllipsis() {
    doTest("Clazz[Any, [float], [float]]", """
      class Clazz[**P1, **P2 = P1, **P3 = P2]: ...
      expr = Clazz[..., [float]]()
      """);
  }

  public void testDataclassTransformConstructorSignature() {
    doTestExpressionUnderCaret("(id: int, name: str) -> MyClass", """
      from typing import dataclass_transform
      
      @dataclass_transform()
      def deco(cls):
          ...
      
      @deco
      class MyClass:
           id: int
           name: str
      
      MyCl<caret>ass()
      """);
  }

  public void testDataclassTransformConstructorSignatureDecoratedBaseClassAttributeExcluded() {
    doTestExpressionUnderCaret("(id: int, name: str) -> SubSub", """
      from typing import dataclass_transform
      
      @dataclass_transform()
      class Base:
          excluded: int
      
      class Sub(Base):
          id: int
      
      class SubSub(Sub):
           name: str
      
      Sub<caret>Sub()
      """);
  }

  public void testDataclassTransformConstructorSignatureMetaClassBaseClassAttributeNotExcluded() {
    doTestExpressionUnderCaret("(included: int, id: int, name: str) -> SubSub", """
      from typing import dataclass_transform
     
      @dataclass_transform()
      class Meta(type):
          pass
      
      class Base(metaclass=Meta):
          included: int
      
      class Sub(Base):
          id: int
      
      class SubSub(Sub):
          name: str

      Sub<caret>Sub()
      """);
  }

  public void testDataclassTransformOverloads() {
    doTestExpressionUnderCaret("(id: int, name: str) -> MyClass", """
      from typing import dataclass_transform, overload
      
      @overload
      def deco(name: str):
          ...
      
      
      @dataclass_transform()
      @overload
      def deco(cls: type):
          ...
      
      @overload
      def deco():
          ...
      
      def deco(*args, **kwargs):
          ...
      
      @deco
      class MyClass:
           id: int
           name: str
      
      MyCl<caret>ass()
      """);
  }

  public void testDataclassTransformOwnKwOnlyOmittedAndTakenFromKwOnlyDefault() {
    doTestExpressionUnderCaret("(*, id: int, name: str) -> MyClass", """
      from typing import dataclass_transform, Callable
      
      
      @dataclass_transform(kw_only_default=True)
      def deco(**kwargs) -> Callable[[type], type]:
          ...
      
      
      @deco(frozen=True)
      class MyClass:
          id: int
          name: str
      
      
      My<caret>Class()
      """);
  }

  public void testDataclassTransformFieldSpecifierKwOnlyDefaultOverridesDecoratorsKwOnly() {
    doTestExpressionUnderCaret("(id: str, *, addr: list[str]) -> Order", """
      from typing import Callable, dataclass_transform
      
      def my_field(kw_only=False):
          ...
      
      @dataclass_transform(field_specifiers=(my_field,))
      def my_dataclass(**kwargs) -> Callable[[type], type]:
          ...
      
      @my_dataclass(kw_only=True)
      class Order:
          id: str = my_field()
          addr: list[str]
      
      Ord<caret>er()
      """);
  }

  public void testDataclassTransformFieldSpecifierKwOnlyDefaultOverridesDecoratorsKwOnlyDefault() {
    doTestExpressionUnderCaret("(id: str, *, addr: list[str]) -> Order", """
      from typing import Callable, dataclass_transform
      
      def my_field(kw_only=False):
          ...
      
      @dataclass_transform(kw_only_default=True, field_specifiers=(my_field,))
      def my_dataclass(**kwargs) -> Callable[[type], type]:
          ...
      
      @my_dataclass()
      class Order:
          id: str = my_field()
          addr: list[str]
      
      Ord<caret>er()
      """);
  }

  public void testDataclassTransformFieldSpecifierKwOnlyOverridesDecoratorsKwOnly() {
    doTestExpressionUnderCaret("(id: str, *, addr: list[str]) -> Order", """
      from typing import Callable, dataclass_transform
      
      def my_field(kw_only=False):
          ...
      
      @dataclass_transform(field_specifiers=(my_field,))
      def my_dataclass(**kwargs) -> Callable[[type], type]:
          ...
      
      @my_dataclass(kw_only=True)
      class Order:
          id: str = my_field(kw_only=False)
          addr: list[str]
      
      Ord<caret>er()
      """);
  }

  public void testDataclassTransformFieldSpecifierKwOnlyOverridesDecoratorsKwOnlyDefault() {
    doTestExpressionUnderCaret("(id: str, *, addr: list[str]) -> Order", """
      from typing import Callable, dataclass_transform
      
      def my_field(kw_only=False):
          ...
      
      @dataclass_transform(kw_only_default=True, field_specifiers=(my_field,))
      def my_dataclass(**kwargs) -> Callable[[type], type]:
          ...
      
      @my_dataclass()
      class Order:
          id: str = my_field(kw_only=False)
          addr: list[str]
      
      Ord<caret>er()
      """);
  }

  public void testDataclassTransformDecoratedFunctionType() {
    doTest("(cls: Any) -> None","""
             from typing import dataclass_transform
             
             @dataclass_transform()
             def my_dataclass(cls): ...
             
             expr = my_dataclass
             """);
  }

  // PY-76076
  public void testGenericAliasUnderVersionGuard() {
    doMultiFileStubAwareTest("list[str]", """
      from mod import f
      
      expr = f("foo")
      """);
  }

  // PY-76076
  public void testGenericMethodReturnTypeImportedUnderVersionGuard() {
    doMultiFileStubAwareTest("list[str]", """
      from mod import C
      expr = C().m()
      """);
  }

  // PY-60968
  public void testGenericMethodReturnTypeImportedUnderVersionGuardInStub() {
    doMultiFileStubAwareTest("list[str]", """
      from mod import C
      expr = C().m()
      """);
  }

  // PY-76076
  public void testFunctionDefinitionUnderVersionGuard() {
    doTest("list[str]", """
      import sys
      from typing import TypeVar
      
      T = TypeVar("T")
      
      if sys.version_info >= (3,):
          def f(x: T) -> list[T]: ...
      else:
          def f(x: T) -> set[T]: ...
      
      expr = f("foo")
      """);
  }

  // PY-76076
  public void testClassDefinitionUnderVersionGuard() {
    doTest("list[str]", """
      import sys
      from typing import TypeVar
      
      T = TypeVar("T")
      
      if sys.version_info >= (3,):
          class C:
              def m(self, x: T) -> list[T]: ...
      else:
          class C:
              def m(self, x: T) -> set[T]: ...
      
      expr = C().m("foo")
      """);
  }

  // PY-76076
  public void testVariableDefinitionUnderVersionGuard() {
    doTest("int", """
      import sys
      
      if sys.version_info < (3, 0):
          x: str = "foo"
      else:
          x: int = 42
      
      expr = x
      """);
  }

  // PY-77168
  public void testReferencingImportedTypeFromUnmatchedVersionGuard() {
    doTest("Literal[42]", """
      from typing import Literal
      import sys
      
      if sys.version_info < (3, 0):
          expr: Literal[42]
      """);
  }

  // PY-77168
  public void testReferencingTopLevelTypeFromUnmatchedVersionGuard() {
    doTest("int", """
      import sys
      
      type Alias = int
      if sys.version_info < (3, 0):
          expr: Alias
      """);
  }
  
  // PY-76243
  public void testGenericClassDeclaredInStubPackage() {
    runWithAdditionalClassEntryInSdkRoots("types/" + getTestName(false) + "/site-packages", () -> {
      doTest("MyClass[int]",
             """
               from pkg.mod import MyClass
               expr: MyClass[int]
               """);
    });
  }

  // PY-36205
  public void testIterateEnum() {
    doTest("Foo",
           """
             from enum import Enum
             class Foo(str, Enum):
                 ONE = 1
             for expr in Foo:
                 pass
             """);
  }

  // PY-77074
  public void testEnumIndexer() {
    doTest("Color", """
      from enum import Enum
      
      class Color(Enum):
        RED = 1
      
      expr = Color["RED"]
      """);
  }

  // PY-76149
  public void testDataclassTransformConstructorSignatureWithFieldsAnnotatedWithDescriptor() {
    doTestExpressionUnderCaret("(id: int, name: str) -> MyClass", """
      from typing import dataclass_transform
      
      @dataclass_transform()
      def deco(cls):
          ...
      
      class MyIdDescriptor:
          def __set__(self, obj: object, value: int) -> None:
              ...
      
      class MyNameDescriptor:
          def __set__(self, obj: object, value: str) -> None:
              ...
      
      @deco
      class MyClass:
           id: MyIdDescriptor
           name: MyNameDescriptor
      
      MyCl<caret>ass()
      """);
  }

  // PY-76149
  public void testDataclassTransformConstructorSignatureWithFieldsAnnotatedWithGenericDescriptor() {
    doTestExpressionUnderCaret("(id: int, name: str) -> MyClass", """
      from typing import dataclass_transform, TypeVar, Generic
      
      T = TypeVar("T")
      
      @dataclass_transform()
      def deco(cls):
          ...
      
      class MyDescriptor(Generic[T]):
          def __set__(self, obj: object, value: T) -> None:
              ...
      
      @deco
      class MyClass:
           id: MyDescriptor[int]
           name: MyDescriptor[str]
      
      MyCl<caret>ass()
      """);
  }

  // PY-76149
  public void testDataclassTransformConstructorSignatureWithFieldsAnnotatedWitExplicitAny() {
    doTestExpressionUnderCaret("(id: int, name: str, payload: Any, payload_length: int) -> MyClass", """
      from typing import dataclass_transform, TypeVar, Generic, Any
      
      T = TypeVar("T")
      
      @dataclass_transform()
      def deco(cls):
          ...
      
      class MyDescriptor(Generic[T]):
          def __set__(self, obj: object, value: T) -> None:
              ...
      
      class Anything:
          def __set__(self, obj: object, value: Any) -> None:
              ...
      
      @deco
      class MyClass:
          id: MyDescriptor[int]
          name: MyDescriptor[str]
          payload: Anything
          payload_length: MyDescriptor[int]
      
      My<caret>Class()
      """);
  }

  public void testTypeAliasToAny() {
    doTest("int | Any", """
      from typing import Any, TypeAlias
      
      Plug: TypeAlias = Any
      expr: int | Plug
      """);
  }

  public void testNewStyleTypeAliasToAny() {
    doTest("int | Any", """
      from typing import Any
      
      type Plug = Any
      expr: int | Plug
      """);
  }

  // PY-36416
  public void testReturnTypeOfNonAnnotatedAsyncOverride() {
    doTest("Coroutine[Any, Any, str]", """
      class Base:
          async def get(self) -> str:
              ...
      
      class Specific(Base):
          async def get(self):
              ...
      
      expr = Specific().get()
      """);
  }

  // PY-40458
  public void testReturnTypeOfNonAnnotatedAsyncOverrideOfNonAsyncMethod() {
    doTest("AsyncGenerator[int, Any]", """
      from typing import AsyncIterator, TypeGuard, Protocol
      
      class Base(Protocol):
          def get(self) -> AsyncIterator[int]:
              ...
      
      class Specific(Base):
          async def get(self):
              yield 42
      
      expr = Specific().get()
      """);
  }

  // PY-40458
  public void testReturnTypeOfNonAnnotatedAsyncOverrideOfAsyncGeneratorMethod() {
    doTest("AsyncIterator[int]", """
      from typing import AsyncIterator, TypeGuard, Protocol
      
      class Base(Protocol):
          async def get(self) -> AsyncIterator[int]:
              if False: yield
      
      class Specific(Base):
          async def get(self):
              yield 42
      
      expr = Specific().get()
      """);
  }

  // PY-77541
  public void testParamSpecBoundToAnotherParamSpecInCustomGeneric() {
    doTest("MyCallable[**P2, R2]", """
                   class MyCallable[**P, R]:
                       def __call__(self, *args: P.args, **kwargs: P.kwargs):
                           ...
                   
                   def f[**P, R](callback: MyCallable[P, R]) -> MyCallable[P, R]:
                       ...
                   
                   def g[**P2, R2](callback: MyCallable[P2, R2]) -> MyCallable[P2, R2]:
                       expr = f(callback)
                   """);
  }

  // PY-77541
  public void testParamSpecBoundToConcatenateInCustomGeneric() {
    doTest("MyCallable[Concatenate(int, **P2), R2]", """
                   from typing import Concatenate
                   
                   class MyCallable[**P, R]:
                       def __call__(self, *args: P.args, **kwargs: P.kwargs):
                           ...
                   
                   def f[**P, R](callback: MyCallable[P, R]) -> MyCallable[P, R]:
                       ...
                   
                   def g[**P2, R2](callback: MyCallable[Concatenate[int, P2], R2]) -> MyCallable[P2, R2]:
                       expr = f(callback)
                   """);
  }

  // PY-79060
  public void testParamSpecInsideConcatenateBoundToCallableParameterListInCustomGeneric() {
    doTest("MyCallable[[int, n: int, s: str]]", """
      from typing import Concatenate, Callable, Any
      
      class MyCallable[**P1]:    ...
      
      def f[**P2](fn: Callable[P2, Any]) -> MyCallable[Concatenate[int, P2]]: ...
      
      def expects_int_str(n: int, s: str) -> None: ...
      
      expr = f(expects_int_str)
      """);
  }

  // PY-79060
  public void testParamSpecInsideConcatenateBoundToAnotherParamSpecInCustomGeneric() {
    doTest("MyCallable[Concatenate(int, **P4)]", """
      from typing import Concatenate, Callable, Any
      
      class MyCallable[**P1]:    ...
      
      def f[**P2](fn: Callable[P2, Any]) -> MyCallable[Concatenate[int, P2]]: ...
      
      def param_spec_replaced_with_another_param_spec[**P4](fn: Callable[P4, Any]):
          expr = f(fn)
      """);
  }

  // PY-79060
  public void testParamSpecInsideConcatenateBoundToConcatenateInCustomGeneric() {
    doTest("MyCallable[Concatenate(int, int, **P3)]", """
      from typing import Concatenate, Callable, Any
      
      class MyCallable[**P1]:    ...
      
      def f[**P2](fn: Callable[P2, Any]) -> MyCallable[Concatenate[int, P2]]: ...
      
      def param_spec_replaced_with_concatenate[**P3](fn: Callable[Concatenate[int, P3], Any]):
          expr = f(fn)
      """);
  }

  // PY-77940
  public void testUnderscoredNameInPyiStub() {
    doMultiFileStubAwareTest("int", """
      from lib import f
      
      expr = f()
      """);
  }

  // PY-77601
  public void testParamSpecCorrectlyParameterizedWhenItIsOnlyGenericParam() {
    doTest("(str, int, bool) -> int", """
      from typing import ParamSpec, Callable, Generic
      
      P = ParamSpec("P")
      
      class MyClass(Generic[P]):
          def call(self) -> Callable[P, int]: ...
      c = MyClass[str, int, bool]()
      expr = c.call()
      """);
  }

  // PY-77601
  public void testParamSpecNotMappedToSingleTypeWithoutSquareBrackets() {
    doTest("Any", """
      from typing import ParamSpec, Callable, Generic, TypeVar
      
      P = ParamSpec("P")
      T = TypeVar("T")
      
      class MyClass(Generic[T, P]):
          def call(self) -> Callable[P, int]: ...
      c = MyClass[str, int]()
      expr = c.call()
      """);
  }

  // PY-77601
  public void testParamSpecMappedToSingleTypeWithSquareBrackets() {
    doTest("(int) -> str", """
      from typing import ParamSpec, Callable, Generic, TypeVar
      
      P = ParamSpec("P")
      T = TypeVar("T")
      
      class MyClass(Generic[T, P]):
          def call(self) -> Callable[P, T]: ...
      c = MyClass[str, [int]]()
      expr = c.call()
      """);
  }

  // PY-78044
  public void testGeneratorYieldsSelf() {
    doTest("A", """
      from collections.abc import Generator
      from typing import Self
      
      class A:
          @classmethod
          def f(cls) -> Generator[Self, None, None]:
              pass
      
      for x in A.f():
          expr = x
      """);
  }

  // PY-78044
  public void testGeneratorYieldsSelfNested() {
    doTest("C", """
      from collections.abc import Generator
      from typing import Self
      
      class A:
          @classmethod
          def f(cls) -> Generator[Self, None, None]:
              pass
      
      class B(A): ...
      class C(B): ...
      
      for x in C.f():
          expr = x
      """);
  }

  // PY-43122
  public void testPropertyOfImportedClass() {
    doMultiFileStubAwareTest("str",
                             """
                               from mod import A, B
                               
                               a = A()
                               b = B(a)
                               expr = b.b_attr
                               """);
  }

  // PY-43122
  public void testPropertyOfClass() {
    doTest("str",
                             """
                               class A:
                                   def __init__(self) -> None:
                                       pass
                               
                                   @property
                                   def a_property(self) -> str:
                                       return 'foo'
                               
                               
                               class B:
                                   def __init__(self, a: A) -> None:
                                       self.b_attr = a.a_property
                               
                               
                               a = A()
                               b = B(a)
                               expr = b.b_attr
                               """);
  }

  // PY-79480
  public void testInheritedAttributeWithTypeAnnotationInParentConstructor() {
    doTest("str | None", """
      import typing

      class FakeBase:
          def __init__(self):
              self._some_var: typing.Optional[str] = ""

      class Fake(FakeBase):
          def __init__(self):
              super().__init__()
              self._some_var = None

          def some_method(self):
              expr = self._some_var
      """);
  }

  public void testInheritedAttributeWithTypeAnnotationInParent() {
    doTest("str | None", """
      import typing

      class FakeBase:
          _some_var: typing.Optional[str]

      class Fake(FakeBase):
          def __init__(self):
              super().__init__()
              self._some_var = None

          def some_method(self):
              expr = self._some_var
      """);
  }

  public void testInheritedAttributeWithTypeAnnotationInChild() {
    doTest("str | None", """
      import typing

      class FakeBase:
          def __init__(self):
              self._some_var = 1

      class Fake(FakeBase):
          def __init__(self):
              super().__init__()
              self._some_var: typing.Optional[str] = None

          def some_method(self):
              expr = self._some_var
      """);
  }

  // PY-80427
  public void testNoneTypeType() {
    doTest("type[None]",
           "expr = type(None)");
  }

  // PY-76908
  public void testSequenceUnpackedTuple() {
    doTest("Sequence[int | str]",
           """
            from typing import Sequence, TypeVar
            T = TypeVar("T")
            def test_seq(x: Sequence[T]) -> Sequence[T]:
                return x
            def func(p: tuple[int, *tuple[str, ...]]):
                expr = test_seq(p)
            """);
  }

  // PY-76908
  public void testSequenceDeepUnpackedTuple() {
    doTest("Sequence[int | complex | str]",
           """
            from typing import Sequence, TypeVar
            T = TypeVar("T")
            def test_seq(x: Sequence[T]) -> Sequence[T]:
                return x
            def func(p: tuple[int, *tuple[complex, *tuple[str, ...]]]):
                expr = test_seq(p)
            """);
  }

  // PY-82454
  public void testMethodReturningTypeParameterCalledOnNonParameterizedGenericWithDefault() {
    doTest("str", """
      class Box[T=str]:
          def m(self) -> T:
              ...
      
      def f() -> Box:
          ...
      
      expr = f().m()
      """);
  }

  // PY-82454
  public void testAttributeOfTypeParameterTypeAccessedOnNonParameterizedGenericWithDefault() {
    doTest("str", """
      class Box[T=str]:
          attr: T
      
      def f() -> Box:
          ...
      
      expr = f().attr
      """);
  }

  // PY-82454
  public void testNonParameterizedGenericWithDefaultUsedInOtherType() {
    doTest("list[Box[str]]", """
      class Box[T=str]:
          def m(self) -> T:
              ...
      
      def f() -> list[Box]:
          ...
      
      expr = f()
      """);
  }

  // PY-82454
  public void testMethodReturningSelfCalledOnNonParameterizedGenericWithDefault() {
    doTest("Box[str]", """
      from typing import Self
      
      class Box[T=str]:
          def m(self) -> Self:
              ...
      
      def f() -> Box:  # not parameterized, simulating open() -> TextIOWrapper
          ...
      
      expr = f().m()
      """);
  }

  // PY-82454
  public void testMethodReturningTypeParameterizedWithSelfCalledOfNonParameterizedGenericWithDefault() {
    doTest("list[Box[str]]", """
      from typing import Self
      
      class Box[T=str]:
          def m(self) -> list[Self]:
              ...
      
      def f() -> Box:  # not parameterized, simulating open() -> TextIOWrapper
          ...
      
      expr = f().m()
      """);
  }

  // PY-82454
  public void testMethodReturningSelfCalledOnNonParameterizedGenericWithDefaultAndBound() {
    doTest("Box[str]", """
      from typing import Self
      
      class Box[T : str = str]:
          def m(self) -> Self:
              ...
      
      def f() -> Box:  # not parameterized, simulating open() -> TextIOWrapper
          ...
      
      expr = f().m()
      """);
  }

  private void doTestNoInjectedText(@NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(getElementAtCaret());
    assertNull(host);
  }

  private void doTestInjectedText(@NotNull String text, @NotNull String expected) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(getElementAtCaret());
    assertNotNull(host);
    final List<Pair<PsiElement, TextRange>> files = languageManager.getInjectedPsiFiles(host);
    assertNotNull(files);
    assertFalse(files.isEmpty());
    final PsiElement injected = files.get(0).getFirst();
    assertEquals(expected, injected.getText());
    assertFalse(PsiTreeUtil.hasErrorElements(injected));
  }

  private void doTest(@NotNull String expectedType, @NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    final TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    final TypeEvalContext userInitiated = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()).withTracing();
    assertType("Failed in code analysis context", expectedType, expr, codeAnalysis);
    assertType("Failed in user initiated context", expectedType, expr, userInitiated);
  }

  private void doTestExpressionUnderCaret(@NotNull String expectedType, @NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PyExpression expr = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PyExpression.class);
    TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    TypeEvalContext userInitiated = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()).withTracing();
    assertType("Failed in code analysis context", expectedType, expr, codeAnalysis);
    assertType("Failed in user initiated context", expectedType, expr, userInitiated);
  }

  private void doMultiFileStubAwareTest(@NotNull final String expectedType, @NotNull final String text) {
    myFixture.copyDirectoryToProject("types/" + getTestName(false), "");
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);

    final TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    assertType("Failed in code analysis context", expectedType, expr, codeAnalysis);
    assertProjectFilesNotParsed(expr.getContainingFile());

    final TypeEvalContext userInitiated = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()).withTracing();
    assertType("Failed in user initiated context", expectedType, expr, userInitiated);
  }
}
