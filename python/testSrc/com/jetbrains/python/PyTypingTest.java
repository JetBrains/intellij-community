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
  }

  // PY-18427
  public void testConditionalType() {
    doTest("int | str",
           """
             if something:
                 Type = int
             else:
                 Type = str

             def f(expr: Type):
                 pass
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
    doTest("tuple",
           """
             d = {
                 int: lambda: ()
             }
             expr = d[int]()""");
  }

  // PY-20057
  public void testClassObjectType() {
    doTest("Type[MyClass]",
           """
             from typing import Type

             class MyClass:
                 pass

             def f(x: Type[MyClass]):\s
                 expr = x""");
  }

  // PY-20057
  public void testConstrainedClassObjectTypeOfParam() {
    doTest("Type[T]",
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
    doTest("Type[int]",
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
    doTest("Type[int | str]",
           """
             from typing import Type, Union

             def f(x: Type[Union[int, str]]):
                 expr = x""");
  }

  // PY-23053
  public void testUnboundGenericMatchesClassObjectTypes() {
    doTest("Type[str]",
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
    doTest("Type[str]",
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
    doMultiFileStubAwareTest("list | int",
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
    doTest("Type[str | dict | int]",
           """
             a = list
             if issubclass(a, str | dict | int):
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

  // PY-44974
  public void testBitwiseOrUnionIsInstanceUnionInTuple() {
    doTest("str | list | dict | bool | None",
           """
             a = 42
             if isinstance(a, (str, (list | dict), bool | None)):
                 expr = a""");
  }

  // PY-44974
  public void testBitwiseOrUnionOfUnionsIsInstance() {
    doTest("dict | str | bool | list",
           """
             from typing import Union
             a = 42
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
                   Type = int
               else:
                   Type = str

               def f(expr: Type):
                   pass
               """);
    });
  }

  // PY-44974
  public void testWithoutFromFutureImport() {
    runWithLanguageLevel(LanguageLevel.PYTHON39, () -> {
      doTest("Union[int, str]",
             """
               if something:
                   Type = int
               else:
                   Type = str

               def f(expr: Type):
                   pass
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
    doTest("tuple[str, list, dict, bool, int]",
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
    doMultiFileStubAwareTest("list | int",
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
  public void testTextIOInferedWithContextManagerDecorator() {
    doTest("TextIO",
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
