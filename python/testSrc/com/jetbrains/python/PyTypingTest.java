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
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tests for a type system based on mypy's typing module.
 *
 * @author vlan
 */
public class PyTypingTest extends PyTestCase {
  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.PYTHON34);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      setLanguageLevel(null);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testClassType() {
    doTest("Foo",
           "class Foo:" +
           "    pass\n" +
           "\n" +
           "def f(expr: Foo):\n" +
           "    pass\n");
  }

  public void testClassReturnType() {
    doTest("Foo",
           "class Foo:" +
           "    pass\n" +
           "\n" +
           "def f() -> Foo:\n" +
           "    pass\n" +
           "\n" +
           "expr = f()\n");

  }

  public void testNoneType() {
    doTest("None",
           "def f(expr: None):\n" +
           "    pass\n");
  }

  public void testNoneReturnType() {
    doTest("None",
           "def f() -> None:\n" +
           "    return 0\n" +
           "expr = f()\n");
  }

  public void testUnionType() {
    doTest("Union[int, str]",
           "from typing import Union\n" +
           "\n" +
           "def f(expr: Union[int, str]):\n" +
           "    pass\n");
  }

  public void testBuiltinList() {
    doTest("list",
           "from typing import List\n" +
           "\n" +
           "def f(expr: List):\n" +
           "    pass\n");
  }

  public void testBuiltinListWithParameter() {
    doTest("List[int]",
           "from typing import List\n" +
           "\n" +
           "def f(expr: List[int]):\n" +
           "    pass\n");
  }

  public void testBuiltinDictWithParameters() {
    doTest("Dict[str, int]",
           "from typing import Dict\n" +
           "\n" +
           "def f(expr: Dict[str, int]):\n" +
           "    pass\n");
  }

  public void testBuiltinTuple() {
    doTest("tuple",
           "from typing import Tuple\n" +
           "\n" +
           "def f(expr: Tuple):\n" +
           "    pass\n");
  }

  public void testBuiltinTupleWithParameters() {
    doTest("Tuple[int, str]",
           "from typing import Tuple\n" +
           "\n" +
           "def f(expr: Tuple[int, str]):\n" +
           "    pass\n");
  }

  public void testAnyType() {
    doTest("Any",
           "from typing import Any\n" +
           "\n" +
           "def f(expr: Any):\n" +
           "    pass\n");
  }

  public void testGenericType() {
    doTest("A",
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('A')\n" +
           "\n" +
           "def f(expr: T):\n" +
           "    pass\n");
  }

  public void testGenericBoundedType() {
    doTest("T",
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('T', int, str)\n" +
           "\n" +
           "def f(expr: T):\n" +
           "    pass\n");
  }

  public void testParameterizedClass() {
    doTest("C[int]",
           "from typing import Generic, TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C(Generic[T]):\n" +
           "    def __init__(self, x: T):\n" +
           "        pass\n" +
           "\n" +
           "expr = C(10)\n");
  }

  public void testParameterizedClassWithConstructorNone() {
    doTest("C[int]",
           "from typing import Generic, TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C(Generic[T]):\n" +
           "    def __init__(self, x: T) -> None:\n" +
           "        pass\n" +
           "\n" +
           "expr = C(10)\n");
  }

  public void testParameterizedClassMethod() {
    doTest("int",
           "from typing import Generic, TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C(Generic[T]):\n" +
           "    def __init__(self, x: T):\n" +
           "        pass\n" +
           "    def foo(self) -> T:\n" +
           "        pass\n" +
           "\n" +
           "expr = C(10).foo()\n");
  }

  public void testParameterizedClassInheritance() {
    doTest("int",
           "from typing import Generic, TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class B(Generic[T]):\n" +
           "    def foo(self) -> T:\n" +
           "        pass\n" +
           "class C(B[T]):\n" +
           "    def __init__(self, x: T):\n" +
           "        pass\n" +
           "\n" +
           "expr = C(10).foo()\n");
  }

  public void testAnyStrUnification() {
    doTest("bytes",
           "from typing import AnyStr\n" +
           "\n" +
           "def foo(x: AnyStr) -> AnyStr:\n" +
           "    pass\n" +
           "\n" +
           "expr = foo(b'bar')\n");
  }

  public void testAnyStrForUnknown() {
    doTest("Union[str, bytes]",
           "from typing import AnyStr\n" +
           "\n" +
           "def foo(x: AnyStr) -> AnyStr:\n" +
           "    pass\n" +
           "\n" +
           "def bar(x):\n" +
           "    expr = foo(x)\n");
  }

  public void testCallableType() {
    doTest("(int, str) -> str",
           "from typing import Callable\n" +
           "\n" +
           "def foo(expr: Callable[[int, str], str]):\n" +
           "    pass\n");
  }

  public void testTypeInStringLiteral() {
    doTest("C",
           "class C:\n" +
           "    def foo(self, expr: 'C'):\n" +
           "        pass\n");
  }

  public void testQualifiedTypeInStringLiteral() {
    doTest("str",
           "import typing\n" +
           "\n" +
           "def foo(x: 'typing.AnyStr') -> typing.AnyStr:\n" +
           "    pass\n" +
           "\n" +
           "expr = foo('bar')\n");
  }

  public void testOptionalType() {
    doTest("Optional[int]",
           "from typing import Optional\n" +
           "\n" +
           "def foo(expr: Optional[int]):\n" +
           "    pass\n");
  }

  // PY-28032
  public void testOptionalOfAny() {
    doTest("Optional[Any]",
           "from typing import Optional, Any\n" +
           "\n" +
           "x = None  # type: Optional[Any]\n" +
           "expr = x\n");
  }

  public void testOptionalFromDefaultNone() {
    doTest("Optional[int]",
           "def foo(expr: int = None):\n" +
           "    pass\n");
  }

  public void testFlattenUnions() {
    doTest("Union[int, str, list]",
           "from typing import Union\n" +
           "\n" +
           "def foo(expr: Union[int, Union[str, list]]):\n" +
           "    pass\n");
  }

  public void testCast() {
    doTest("str",
           "from typing import cast\n" +
           "\n" +
           "def foo(x):\n" +
           "    expr = cast(str, x)\n");
  }

  public void testComment() {
    doTest("int",
           "def foo(x):\n" +
           "    expr = x  # type: int\n");
  }

  public void testMultiAssignmentComment() {
    doTest("Tuple[int, str]",
           "def foo(x):\n" +
           "    c1, c2 = x  # type: int, str\n" +
           "    expr = c1, c2\n");
  }

  // PY-19220
  public void testMultiLineAssignmentComment() {
    doTest("List[str]",
           "from typing import List\n" +
           "\n" +
           "expr = [\n" +
           "    a,\n" +
           "    b,\n" +
           "]  # type: List[str]");
  }

  public void testForLoopComment() {
    doTest("int",
           "def foo(xs):\n" +
           "    for expr, x in xs:  # type: int, str\n" +
           "        pass\n");
  }

  public void testWithComment() {
    doTest("int",
           "def foo(x):\n" +
           "    with x as expr:  # type: int\n" +
           "        pass\n");
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
           "from typing import List\n" +
           "\n" +
           "xs = [expr for expr in range(10)]  # type: List[int]");
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
    doTestInjectedText("class C:\n" +
                       "    def foo(self, expr: '<caret>C'):\n" +
                       "        pass\n",
                       "C");
  }

  public void testStringLiteralInjectionParameterizedType() {
    doTestInjectedText("from typing import Union, List\n" +
                       "\n" +
                       "class C:\n" +
                       "    def foo(self, expr: '<caret>Union[List[C], C]'):\n" +
                       "        pass\n",
                       "Union[List[C], C]");
  }

  // PY-37515
  public void testNoStringLiteralInjectionUnderCall() {
    doTestNoInjectedText("class Model:\n" +
                         "    field: call('<caret>List[str]')");
  }

  // PY-15810
  public void testNoStringLiteralInjectionForNonTypingStrings() {
    doTestNoInjectedText("class C:\n" +
                         "    def foo(self, expr: '<caret>foo bar'):\n" +
                         "        pass\n");
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
           "from typing import Iterable\n" +
           "\n" +
           "def foo() -> Iterable[int]:\n" +
           "    pass\n" +
           "\n" +
           "for expr in foo():\n" +
           "    pass\n");
  }

  // PY-16353
  public void testAssignedType() {
    doTest("Iterable[int]",
           "from typing import Iterable\n" +
           "\n" +
           "IntIterable = Iterable[int]\n" +
           "\n" +
           "def foo() -> IntIterable:\n" +
           "    pass\n" +
           "\n" +
           "expr = foo()\n");
  }

  // PY-16267
  public void testGenericField() {
    doTest("str",
           "from typing import TypeVar, Generic\n" +
           "\n"                                    +
           "T = TypeVar('T', covariant=True)\n"    +
           "\n"                                    +
           "class C(Generic[T]):\n"                +
           "    def __init__(self, foo: T):\n"     +
           "        self.foo = foo\n"              +
           "\n"                                    +
           "def f() -> C[str]:\n"                  +
           "    return C('test')\n"                +
           "\n"                                    +
           "x = f()\n"                             +
           "expr = x.foo\n");
  }

  // PY-18427
  public void testConditionalType() {
    doTest("Union[int, str]",
           "if something:\n" +
           "    Type = int\n" +
           "else:\n" +
           "    Type = str\n" +
           "\n" +
           "def f(expr: Type):\n" +
           "    pass\n");
  }
  
  // PY-18254
  public void testFunctionTypeComment() {
    doTest("(x: int, args: Tuple[float, ...], kwargs: Dict[str, str]) -> List[bool]",
           "from typing import List\n" +
           "\n" +
           "def f(x, *args, **kwargs):\n" +
           "    # type: (int, *float, **str) -> List[bool]\n" +
           "    pass\n" +
           "\n" +
           "expr = f");
  }

  // PY-18595
  public void testFunctionTypeCommentForStaticMethod() {
    doTest("int",
           "class C:\n" +
           "    @staticmethod\n" +
           "    def m(some_int, some_bool, some_str):\n" +
           "        # type: (int, bool, str) -> bool\n" +
           "        expr = some_int");

  }

  // PY-18726
  public void testFunctionTypeCommentCallableParameter() {
    doTest("(bool, str) -> int",
           "from typing import Callable\n" +
           "\n" +
           "def f(cb):\n" +
           "    # type: (Callable[[bool, str], int]) -> None\n" +
           "    expr = cb");
  }

  // PY-18763  
  public void testCallableTypeWithEllipsis() {
    doTest("(...) -> int",
           "from typing import Callable\n" +
           "\n" +
           "expr = unknown() # type: Callable[..., int]");
  }

  // PY-18763  
  public void testFunctionTypeCommentCallableParameterWithEllipsis() {
    doTest("(...) -> int",
           "from typing import Callable\n" +
           "\n" +
           "def f(cb):\n" +
           "    # type: (Callable[..., int]) -> None\n" +
           "    expr = cb");
  }

  // PY-18726
  public void testFunctionTypeCommentBadCallableParameter1() {
    doTest("Any",
           "from typing import Callable, Tuple\n" +
           "\n" +
           "def f(cb):\n" +
           "    # type: (Callable[Tuple[bool, str], int]) -> None\n" +
           "    expr = cb");

  }

  // PY-18726
  public void testFunctionTypeCommentBadCallableParameter2() {
    doTest("(bool, int) -> Any",
           "from typing import Callable, Tuple\n" +
           "\n" +
           "def f(cb):\n" +
           "    # type: (Callable[[bool, int], [int]]) -> None\n" +
           "    expr = cb");

  }

  // PY-18598
  public void testFunctionTypeCommentEllipsisParameters() {
   doTest("(x: Any, y: Any, z: Any) -> int",
          "def f(x, y=42, z='foo'):\n" +
          "    # type: (...) -> int \n" +
          "    pass\n" +
          "\n" +
          "expr = f");
  }

  // PY-20421
  public void testFunctionTypeCommentSingleElementTuple() {
    doTest("Tuple[int]",
           "from typing import Tuple\n" +
           "\n" +
           "def f():\n" +
           "    # type: () -> Tuple[int]\n" +
           "    pass\n" +
           "\n" +
           "expr = f()");
  }

  // PY-18762
  public void testHomogeneousTuple() {
    doTest("Tuple[int, ...]",
           "from typing import Tuple\n" +
           "\n" +
           "def f(xs: Tuple[int, ...]):\n" +
           "    expr = xs");
  }

  // PY-18762
  public void testHomogeneousTupleIterationType() {
    doTest("int",
           "from typing import Tuple\n" +
           "\n" +
           "xs = unknown() # type: Tuple[int, ...]\n" +
           "\n" +
           "for x in xs:\n" +
           "    expr = x");
  }

  // PY-18762
  public void testHomogeneousTupleUnpackingTarget() {
    doTest("int",
           "from typing import Tuple\n" +
           "\n" +
           "xs = unknown() # type: Tuple[int, ...]\n" +
           "expr, yx = xs");
  }

  // PY-18762
  public void testHomogeneousTupleMultiplication() {
    doTest("Tuple[int, ...]",
           "from typing import Tuple\n" +
           "\n" +
           "xs = unknown() # type: Tuple[int, ...]\n" +
           "expr = xs * 42");
  }

  // PY-18762
  public void testFunctionTypeCommentHomogeneousTuple() {
    doTest("Tuple[int, ...]",
           "from typing import Tuple\n" +
           "\n" +
           "def f(xs):\n" +
           "    # type: (Tuple[int, ...]) -> None\n" +
           "    expr = xs\n");
  }

  // PY-18741
  public void testFunctionTypeCommentWithParamTypeComment() {
    doTest("(x: int, y: bool, z: Any) -> str",
           "def f(x, # type: int \n" +
           "      y # type: bool\n" +
           "      ,z):\n" +
           "    # type: (...) -> str\n" +
           "    pass\n" +
           "\n" +
           "expr = f");
  }

  // PY-18877
  public void testFunctionTypeCommentOnTheSameLine() {
    doTest("(x: int, y: int) -> None",
           "def f(x,\n" +
           "      y):  # type: (int, int) -> None\n" +
           "    pass\n" +
           "\n" +
           "expr = f");
  }
  
  // PY-18386
  public void testRecursiveType() {
    doTest("Union[int, Any]",
           "from typing import Union\n" +
           "\n" +
           "Type = Union[int, 'Type']\n" +
           "expr = 42 # type: Type");
  }

  // PY-18386
  public void testRecursiveType2() {
    doTest("Dict[str, Union[Union[str, int, float], Any]]",
           "from typing import Dict, Union\n" +
           "\n" +
           "JsonDict = Dict[str, Union[str, int, float, 'JsonDict']]\n" +
           "\n" +
           "def f(x: JsonDict):\n" +
           "    expr = x");
  }

  // PY-18386
  public void testRecursiveType3() {
    doTest("Union[Union[str, int], Any]",
           "from typing import Union\n" +
           "\n" +
           "Type1 = Union[str, 'Type2']\n" +
           "Type2 = Union[int, Type1]\n" +
           "\n" +
           "expr = None # type: Type1");
  }

  // PY-19858
  public void testGetListItemByIntegral() {
    doTest("list",
           "from typing import List\n" +
           "\n" +
           "def foo(x: List[List]):\n" +
           "    expr = x[0]\n");
  }

  // PY-19858
  public void testGetListItemByIndirectIntegral() {
    doTest("list",
           "from typing import List\n" +
           "\n" +
           "def foo(x: List[List]):\n" +
           "    y = 0\n" +
           "    expr = x[y]\n");
  }

  // PY-19858
  public void testGetSublistBySlice() {
    doTest("List[list]",
           "from typing import List\n" +
           "\n" +
           "def foo(x: List[List]):\n" +
           "    expr = x[1:3]\n");
  }

  // PY-19858
  public void testGetSublistByIndirectSlice() {
    doTest("List[list]",
           "from typing import List\n" +
           "\n" +
           "def foo(x: List[List]):\n" +
           "    y = slice(1, 3)\n" +
           "    expr = x[y]\n");
  }

  // PY-19858
  public void testGetListItemByUnknown() {
    doTest("Union[list, List[list]]",
           "from typing import List\n" +
           "\n" +
           "def foo(x: List[List]):\n" +
           "    expr = x[y]\n");
  }

  public void testGetListOfListsItemByIntegral() {
    doTest("Any",
           "from typing import List\n" +
           "\n" +
           "def foo(x: List[List]):\n" +
           "    sublist = x[0]\n" +
           "    expr = sublist[0]\n");
  }

  public void testLocalVariableAnnotation() {
    doTest("int",
           "def f():\n" +
           "    x: int = undefined()\n" +
           "    expr = x");
  }

  // PY-21864
  public void testLocalVariableAnnotationAheadOfTimeWithTarget() {
    doTest("int",
           "x: int\n" +
           "with foo() as x:\n" +
           "    expr = x\n");
  }

  // PY-21864
  public void testTopLevelVariableAnnotationAheadOfTimeInAnotherFileWithTarget() {
    doMultiFileStubAwareTest("int",
                             "from other import x\n" +
                             "\n" +
                             "expr = x");
  }

  public void testLocalVariableAnnotationAheadOfTimeForTarget() {
    doTest("int",
           "x: int\n" +
           "for x in foo():\n" +
           "    expr = x\n");
  }

  // PY-21864
  public void testTopLevelVariableAnnotationAheadOfTimeInAnotherFileForTarget() {
    doMultiFileStubAwareTest("int",
                             "from other import x\n" +
                             "\n" +
                             "expr = x");
  }

  // PY-21864
  public void testLocalVariableAnnotationAheadOfTimeUnpackingTarget() {
    doTest("int",
           "x: int\n" +
           "x, y = foo()\n" +
           "expr = x");
  }

  // PY-21864
  public void testTopLevelVariableAnnotationAheadOfTimeInAnotherFileUnpackingTarget() {
    doMultiFileStubAwareTest("int",
                             "from other import x\n" +
                             "\n" +
                             "expr = x");
  }

  // PY-21864
  public void testLocalVariableAnnotationAheadOfTimeOnlyFirstHintConsidered() {
    doTest("int",
           "x: int\n" +
           "x = foo()\n" +
           "x: str\n" +
           "x = baz()\n" +
           "expr = x");
  }

  // PY-16412
  public void testLocalVariableAnnotationAheadOfTimeExplicitAny() {
    doTest("Any",
           "from typing import Any\n" +
           "\n" +
           "def func(x):\n" +
           "    var: Any\n" +
           "    var = x\n" +
           "    expr = var\n");
  }

  // PY-28032
  public void testClassAttributeAnnotationExplicitAny() {
    doTest("Any",
           "from typing import Any\n" +
           "\n" +
           "class C:\n" +
           "    attr: Any = None\n" +
           "    \n" +
           "    def m(self, x):\n" +
           "        self.attr = x\n" +
           "        expr = self.attr");
  }

  // PY-21864
  public void testClassAttributeAnnotationAheadOfTimeInAnotherFile() {
    doMultiFileStubAwareTest("int",
                             "from other import C\n" +
                             "\n" +
                             "expr = C().attr");
  }

  public void testInstanceAttributeAnnotation() {
    doTest("int",
           "class C:\n" +
           "    attr: int\n" +
           "    \n" +
           "expr = C().attr");
  }

  public void testIllegalAnnotationTargets() {
    doTest("Tuple[Any, int, Any, Any]",
           "(w, _): Tuple[int, Any]\n" +
           "((x)): int\n" +
           "y: bool = z = undefined()\n" +
           "expr = (w, x, y, z)\n");
  }

  // PY-19723
  public void testAnnotatedPositionalArgs() {
    doTest("Tuple[str, ...]",
           "def foo(*args: str):\n" +
           "    expr = args\n");
  }

  // PY-19723
  public void testAnnotatedKeywordArgs() {
    doTest("Dict[str, int]",
           "def foo(**kwargs: int):\n" +
           "    expr = kwargs\n");
  }

  // PY-19723
  public void testTypeCommentedPositionalArgs() {
    doTest("Tuple[str, ...]",
           "def foo(*args  # type: str\n):\n" +
           "    expr = args\n");
  }

  // PY-19723
  public void testTypeCommentedKeywordArgs() {
    doTest("Dict[str, int]",
           "def foo(**kwargs  # type: int\n):\n" +
           "    expr = kwargs\n");
  }

  public void testGenericInheritedSpecificAndGenericParameters() {
    doTest("C[float]",
           "from typing import TypeVar, Generic, Tuple, Iterator, Iterable\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class B(Generic[T]):\n" +
           "    pass\n" +
           "\n" +
           "class C(B[Tuple[int, T]], Generic[T]):\n" +
           "    def __init__(self, x: T) -> None:\n" +
           "        pass\n" +
           "\n" +
           "expr = C(3.14)\n");
  }

  public void testAsyncGeneratorAnnotation() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("AsyncGenerator[int, str]",
                   "from typing import AsyncGenerator\n" +
                   "\n" +
                   "async def g() -> AsyncGenerator[int, str]:\n" +
                   "    s = (yield 42)\n" +
                   "    \n" +
                   "expr = g()")
    );
  }

  public void testCoroutineReturnsGenerator() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> doTest("Coroutine[Any, Any, Generator[int, Any, Any]]",
                   "from typing import Generator\n" +
                   "\n" +
                   "async def coroutine() -> Generator[int, Any, Any]:\n" +
                   "    def gen():\n" +
                   "        yield 42\n" +
                   "    \n" +
                   "    return gen()\n" +
                   "    \n" +
                   "expr = coroutine()")
    );
  }

  public void testGenericRenamedParameter() {
    doTest("int",
           "from typing import TypeVar, Generic\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "V = TypeVar('V')\n" +
           "\n" +
           "class B(Generic[V]):\n" +
           "    def get() -> V:\n" +
           "        pass\n" +
           "\n" +
           "class C(B[T]):\n" +
           "    def __init__(self, x: T) -> None:\n" +
           "        pass\n" +
           "\n" +
           "expr = C(0).get()\n");
  }

  // PY-20057
  public void testClassObjectType() {
    doTest("Type[MyClass]",
           "from typing import Type\n" +
           "\n" +
           "class MyClass:\n" +
           "    pass\n" +
           "\n" +
           "def f(x: Type[MyClass]): \n" +
           "    expr = x");
  }
  
  // PY-20057
  public void testConstrainedClassObjectTypeOfParam() {
    doTest("Type[T]",
           "from typing import Type, TypeVar\n" +
           "\n" +
           "T = TypeVar('T', bound=int)\n" +
           "\n" +
           "def f(x: Type[T]):\n" +
           "    expr = x");
  }
  
  // PY-20057
  public void testFunctionCreatesInstanceFromType() {
    doTest("int",
           "from typing import Type, TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "def f(x: Type[T]) -> T:\n" +
           "    return x()\n" +
           "\n" +
           "expr = f(int)");
  }

  // PY-20057
  public void testFunctionReturnsTypeOfInstance() {
    doTest("Type[int]",
           "from typing import Type, TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "def f(x: T) -> Type[T]:\n" +
           "    return type(T)\n" +
           "    \n" +
           "expr = f(42)");
  }

  // PY-20057
  public void testNonParametrizedTypingTypeMapsToBuiltinType() {
    doTest("type",
           "from typing import Type\n" +
           "\n" +
           "def f(x: Type):\n" +
           "    expr = x");
  }
  
  // PY-20057
  public void testTypingTypeOfAnyMapsToBuiltinType() {
    doTest("type",
           "from typing import Type, Any\n" +
           "\n" +
           "def f(x: Type[Any]):\n" +
           "    expr = x");
  }

  // PY-20057
  public void testIllegalTypingTypeFormat() {
    doTest("Tuple[Any, Any, Any]",
           "from typing import Type, Tuple\n" +
           "\n" +
           "def f(x: Tuple[Type[42], Type[], Type[unresolved]]):\n" +
           "    expr = x");
  }
  
  // PY-20057
  public void testUnionOfClassObjectTypes() {
    doTest("Type[Union[int, str]]",
           "from typing import Type, Union\n" +
           "\n" +
           "def f(x: Type[Union[int, str]]):\n" +
           "    expr = x");
  }

  // PY-23053
  public void testUnboundGenericMatchesClassObjectTypes() {
    doTest("Type[str]",
           "from typing import Generic, TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class Holder(Generic[T]):\n" +
           "    def __init__(self, value: T):\n" +
           "        self._value = value\n" +
           "\n" +
           "    def get(self) -> T:\n" +
           "        return self._value\n" +
           "\n" +
           "expr = Holder(str).get()\n");
  }

  // PY-23053
  public void testListContainingClasses() {
    doTest("Type[str]",
           "xs = [str]\n" +
           "expr = xs.pop()");
  }

  public void testGenericUserFunctionWithManyParamsAndNestedCall() {
    doTest("Tuple[bool, int, str]",
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "U = TypeVar('U')\n" +
           "V = TypeVar('V')\n" +
           "\n" +
           "def myid(x: T) -> T:\n" +
           "    pass\n" +
           "\n" +
           "def f(x: T, y: U, z: V):\n" +
           "    return myid(x), myid(y), myid(z)\n" +
           "\n" +
           "expr = f(True, 1, 'foo')\n");
  }

  // PY-24260
  public void testGenericClassParameterTakenFromGenericClassObject() {
    doTest("MyClass[T]",
           "from typing import TypeVar, Generic, Type\n" +
           "\n" +
           "T = TypeVar(\"T\")\n" +
           "\n" +
           "class MyClass(Generic[T]):\n" +
           "    def __init__(self, type: Type[T]):\n" +
           "        pass\n" +
           "\n" +
           "def f(x: Type[T]):\n" +
           "    expr = MyClass(x)\n");
  }

  // PY-18816
  public void testLocalTypeAlias() {
    doTest("int",
           "def func(g):\n" +
           "    Alias = int\n" +
           "    expr: Alias = g()");
  }

  // TODO same test for variable type comments
  // PY-18816
  public void testLocalTypeAliasInFunctionTypeComment() {
    doTest("int",
           "def func():\n" +
           "    Alias = int\n" +
           "    def g(x):\n" +
           "        # type: (Alias) -> None\n" +
           "        expr = x\n");
  }

  // PY-24729
  public void testAnnotatedInstanceAttributeReferenceOutsideClass() {
    doTest("int",
           "class C:\n" +
           "    attr: int\n" +
           "\n" +
           "    def __init__(self):\n" +
           "        self.attr = 'foo'\n" +
           "\n" +
           "expr = C().attr\n");
  }

  // PY-24729
  public void testAnnotatedInstanceAttributeReferenceInsideClass() {
    doTest("int",
           "class C:\n" +
           "    attr: int\n" +
           "\n" +
           "    def __init__(self):\n" +
           "        self.attr = 'foo'\n" +
           "        \n" +
           "    def m(self):\n" +
           "        expr = self.attr\n");
  }

  // PY-24729
  public void testAnnotatedInstanceAttributeInOtherFile() {
    doMultiFileStubAwareTest("int",
                             "from other import C\n" +
                             "\n" +
                             "expr = C().attr");
  }

  // PY-24990
  public void testSelfAnnotationSameClassInstance() {
    doTest("C",
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C:\n" +
           "    def method(self: T) -> T:\n" +
           "        pass\n" +
           "\n" +
           "expr = C().method()");
  }

  // PY-24990
  public void testSelfAnnotationSubclassInstance() {
    doTest("D",
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C:\n" +
           "    def method(self: T) -> T:\n" +
           "        pass\n" +
           "\n" +
           "class D(C):\n" +
           "    pass\n" +
           "\n" +
           "expr = D().method()");
  }

  // PY-24990
  public void testClsAnnotationSameClassInstance() {
    doTest("C",
           "from typing import TypeVar, Type\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C:\n" +
           "    @classmethod\n" +
           "    def factory(cls: Type[T]) -> T:\n" +
           "        pass\n" +
           "\n" +
           "expr = C.factory()");
  }

  // PY-24990
  public void testClsAnnotationSubclassInstance() {
    doTest("D",
           "from typing import TypeVar, Type\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C:\n" +
           "    @classmethod\n" +
           "    def factory(cls: Type[T]) -> T:\n" +
           "        pass\n" +
           "\n" +
           "class D(C): \n" +
           "    pass\n" +
           "\n" +
           "expr = D.factory()");
  }

  // PY-24990
  public void testClsAnnotationClassMethodCalledOnInstance() {
    doTest("D",
           "from typing import TypeVar, Type\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C:\n" +
           "    @classmethod\n" +
           "    def factory(cls: Type[T]) -> T:\n" +
           "        pass\n" +
           "\n" +
           "class D(C): \n" +
           "    pass\n" +
           "\n" +
           "expr = D().factory()");
  }

  // PY-24990
  public void testSelfAnnotationReceiverUnionType() {
    doTest("Union[A, B]",
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class Base:\n" +
           "    def method(self: T) -> T:\n" +
           "        pass\n" +
           "\n" +
           "class A(Base):\n" +
           "    pass\n" +
           "\n" +
           "class B(Base): \n" +
           "    pass\n" +
           "\n" +
           "expr = (A() or B()).method()");
  }

  // PY-24990
  public void _testClsAnnotationReceiverUnionType() {
    doTest("Union[A, B]",
           "from typing import TypeVar, Type\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class Base:\n" +
           "    @classmethod\n" +
           "    def factory(cls: Type[T]) -> T:\n" +
           "        pass\n" +
           "\n" +
           "class A(Base):\n" +
           "    pass\n" +
           "\n" +
           "class B(Base):\n" +
           "    pass\n" +
           "\n" +
           "expr = (A or B).factory()");
  }

  // PY-24990
  public void testClsAnnotationReceiverUnionTypeClassMethodCalledOnMixedInstanceClassObject() {
    doTest("A",
           "from typing import TypeVar, Type\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class Base:\n" +
           "    @classmethod\n" +
           "    def factory(cls: Type[T]) -> T:\n" +
           "        pass\n" +
           "\n" +
           "class A(Base):\n" +
           "    pass\n" +
           "\n" +
           "expr = (A or A()).factory()");
  }

  // PY-24990
  public void testSelfAnnotationInstanceMethodCalledOnClassObject() {
    doTest("D",
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C:\n" +
           "    def method(self: T) -> T:\n" +
           "        pass\n" +
           "\n" +
           "class D(C):\n" +
           "    pass\n" +
           "\n" +
           "expr = C.method(D())");
  }

  // PY-24990
  public void testSelfAnnotationInTypeCommentSameClassInstance() {
    doTest("C",
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C:\n" +
           "    def method(self):\n" +
           "        # type: (T) -> T\n" +
           "        pass\n" +
           "\n" +
           "expr = C().method()");
  }

  // PY-24990
  public void testSelfAnnotationInTypeCommentSubclassInstance() {
    doTest("D",
           "from typing import TypeVar\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C:\n" +
           "    def method(self):\n" +
           "        # type: (T) -> T\n" +
           "        pass\n" +
           "\n" +
           "class D(C):\n" +
           "    pass\n" +
           "\n" +
           "expr = D().method()");
  }

  // PY-24990
  public void testClsAnnotationInTypeCommentSameClassInstance() {
    doTest("C",
           "from typing import TypeVar, Type\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C:\n" +
           "    @classmethod\n" +
           "    def factory(cls) -> T:\n" +
           "        # type: (Type[T]) -> T\n" +
           "        pass\n" +
           "\n" +
           "expr = C.factory()");
  }

  // PY-24990
  public void testClsAnnotationInTypeCommentSubclassInstance() {
    doTest("D",
           "from typing import TypeVar, Type\n" +
           "\n" +
           "T = TypeVar('T')\n" +
           "\n" +
           "class C:\n" +
           "    @classmethod\n" +
           "    def factory(cls):\n" +
           "        # type: (Type[T]) -> T\n" +
           "        pass\n" +
           "\n" +
           "class D(C): \n" +
           "    pass\n" +
           "\n" +
           "expr = D.factory()");
  }

  // PY-31004
  public void testRecursiveTypeAliasInAnotherFile() {
    doMultiFileStubAwareTest("Union[list, int]",
                             "from other import MyType\n" +
                             "\n" +
                             "expr: MyType = ...");
  }

  // PY-31146
  public void testNoneTypeInAnotherFile() {
    doMultiFileStubAwareTest("(int) -> None",
                             "from other import MyType\n" +
                             "\n" +
                             "expr: MyType = ...\n" +
                             "\n");
  }

  // PY-34478
  public void testTrivialTypeAliasInAnotherFile() {
    doMultiFileStubAwareTest("str",
                             "from other import alias\n" +
                             "\n" +
                             "expr: alias");
  }

  // PY-34478
  public void testTrivialUnresolvedTypeAliasInAnotherFile() {
    doMultiFileStubAwareTest("Any",
                             "from other import alias\n" +
                             "\n" +
                             "expr: alias");
  }

  // PY-34478
  public void testTrivialRecursiveTypeAliasInAnotherFile() {
    doMultiFileStubAwareTest("Any",
                             "from other import alias\n" +
                             "\n" +
                             "expr: alias");
  }

  public void testGenericSubstitutionInDeepHierarchy() {
    doTest("int",
           "from typing import Generic, TypeVar\n" +
           "\n" +
           "T1 = TypeVar('T1')\n" +
           "T2 = TypeVar('T2')\n" +
           "\n" +
           "class Root(Generic[T1, T2]):\n" +
           "    def m(self) -> T2:\n" +
           "        pass\n" +
           "\n" +
           "class Base3(Root[T1, int]):\n" +
           "    pass\n" +
           "\n" +
           "class Base2(Base3[T1]):\n" +
           "    pass\n" +
           "\n" +
           "class Base1(Base2[T1]):\n" +
           "    pass\n" +
           "\n" +
           "class Sub(Base1[T1]):\n" +
           "    pass\n" +
           "\n" +
           "expr = Sub().m()\n");
  }

  // PY-35235
  public void testNoStringLiteralInjectionForTypingLiteral() {
    doTestNoInjectedText("from typing import Literal\n" +
                         "a: Literal[\"f<caret>oo\"]\n");

    doTestNoInjectedText("from typing import Literal\n" +
                         "a: Literal[42, \"f<caret>oo\", True]\n");

    doTestNoInjectedText("from typing import Literal\n" +
                         "MyType = Literal[42, \"f<caret>oo\", True]\n" +
                         "a: MyType\n");
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
