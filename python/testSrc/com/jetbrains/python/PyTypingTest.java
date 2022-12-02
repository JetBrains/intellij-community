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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tests for a type system based on mypy's typing module.
 *
 * @author vlan
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
                 return type(T)
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
                 def factory(cls) -> T:
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
           "from typing import Self\n" +
           "\n" +
           "class A:\n" +
           "    def foo(self) -> Self:\n" +
           "        ...\n" +
           "class B(A):\n" +
           "    pass\n" +
           "expr = B().foo()");
  }

  // PY-53104
  public void testMethodReturnListSelf() {
    doTest("list[B]",
           "from typing import Self\n" +
           "\n" +
           "class A:\n" +
           "    def foo(self) -> list[Self]:\n" +
           "        ...\n" +
           "class B(A):\n" +
           "    pass:\n" +
           "        ...\n" +
           "expr = B().foo()");
  }

  // PY-53104
  public void testClassMethodReturnSelf() {
    doTest("Circle",
           "from typing import Self\n" +
           "\n" +
           "\n" +
           "class Shape:\n" +
           "    @classmethod\n" +
           "    def from_config(cls, config: dict[str, float]) -> Self:\n" +
           "        return cls(config[\"scale\"])\n" +
           "\n" +
           "\n" +
           "class Circle(Shape):\n" +
           "    pass\n" +
           "\n" +
           "\n" +
           "expr = Circle.from_config({})\n");
  }

  // PY-53104
  public void testClassMethodReturnSelfNestedClass() {
    doTest("Circle",
           "from typing import Self\n" +
           "\n" +
           "\n" +
           "class OuterClass:\n" +
           "    class Shape:\n" +
           "        @classmethod\n" +
           "        def from_config(cls, config: dict[str, float]) -> Self:\n" +
           "            return cls(config[\"scale\"])\n" +
           "\n" +
           "    class Circle(Shape):\n" +
           "        pass\n" +
           "\n" +
           "\n" +
           "expr = OuterClass.Circle.from_config({})\n");
  }

  // PY-53104
  public void testNoUnstubInCalculateSelfTypeInFunctionDefinedInImportedFile() {
    doMultiFileStubAwareTest("Clazz",
                    "from other import Clazz\n" +
                    "clz = Clazz()\n" +
                    "expr = clz.foo()\n");
  }

  // PY-53104
  public void testMatchSelfUnionType() {
    doTest("C",
           "from typing import Self\n" +
           "\n" +
           "\n" +
           "class C:\n" +
           "    def method(self) -> Self:\n" +
           "        return self\n" +
           "\n" +
           "\n" +
           "if bool():\n" +
           "    x = 42\n" +
           "else:\n" +
           "    x = C()\n" +
           "\n" +
           "expr = x.method()");
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
