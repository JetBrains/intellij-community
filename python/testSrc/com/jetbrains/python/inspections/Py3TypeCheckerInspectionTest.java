// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class Py3TypeCheckerInspectionTest extends PyInspectionTestCase {
  public static final String TEST_DIRECTORY = "inspections/PyTypeCheckerInspection/";

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypeCheckerInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }

  @Override
  protected String getTestCaseDirectory() {
    return TEST_DIRECTORY;
  }

  // PY-9289
  public void testWithOpenBinaryPy3() {
    doTest();
  }

  // PY-10660
  public void testStructUnpackPy3() {
    doMultiFileTest();
  }

  public void testBuiltinsPy3() {
    doTest();
  }

  // PY-16125
  public void testTypingIterableForLoop() {
    doTest();
  }

  // PY-16146
  public void testTypingListSubscriptionExpression() {
    doTest();
  }

  // PY-16855
  public void testTypingTypeVarWithUnresolvedBound() {
    doTest();
  }

  // PY-16898
  public void testAsyncForIterable() {
    doTest();
  }

  // PY-18275
  public void testStrFormatPy3() {
    doTest();
  }

  // PY-18762
  public void testHomogeneousTuples() {
    doTest();
  }

  // PY-9924
  public void testTupleGetItemWithSlice() {
    doTest();
  }

  // PY-9924
  public void testListGetItemWithSlice() {
    doTest();
  }

  // PY-20460
  public void testStringGetItemWithSlice() {
    doTest();
  }

  // PY-20460
  public void testBytesGetItemWithSlice() {
    doTest();
  }

  // PY-19796
  public void testOrd() {
    doTest();
  }

  // PY-12944
  public void testDelegatedGenerator() {
    doTest();
  }

  // PY-16055
  public void testFunctionReturnTypePy3() {
    doTest();
  }

  // PY-20770
  public void testAsyncForOverAsyncGenerator() {
    doTest();
  }

  // PY-20770
  public void testForOverAsyncGenerator() {
    doTest();
  }

  // PY-20770
  public void testAsyncComprehensionsOverAsyncGenerator() {
    doTest();
  }

  // PY-20770
  public void testAsyncComprehensionsOverGenerator() {
    doTest();
  }

  // PY-20770
  public void testComprehensionsOverAsyncGenerator() {
    doTest();
  }

  // PY-20769
  public void testPathLikePassedToStdlibFunctions() {
    doTest();
  }

  // PY-21048
  public void testAsyncFunctionReturnType() {
    doTest();
  }

  // PY-20967
  public void testAsyncFunctionAnnotatedToReturnNone() {
    doTest();
  }

  // PY-20709
  public void testGeneratorReturnType() {
    doTest();
  }

  // PY-20657, PY-21916
  public void testGeneratorAnnotatedToReturnIterable() {
    doTest();
  }

  // PY-20657, PY-21916
  public void testAsyncGeneratorAnnotatedToReturnAsyncIterable() {
    doTest();
  }

  // PY-21083
  public void testFloatFromhex() {
    doTest();
  }

  // PY-20073
  public void testMapArgumentsInOppositeOrderPy3() {
    doTest();
  }

  // PY-21350
  public void testBuiltinInputPy3() {
    doTest();
  }

  // PY-200057
  public void testClassObjectType() {
    doTest();
  }

  // PY-20057
  public void testTypeAndClassObjectTypesCompatibility() {
    doTest();
  }

  // PY-20057
  public void testClassObjectTypeWithUnion() {
    doTest();
  }

  // PY-22730
  public void testOptionalOfBoundTypeVarInWarnings() {
    doTest();
  }

  // PY-22769
  public void testReplaceCalledOnUnionOfStrAndBytesWithStrArguments() {
    doTest();
  }

  // PY-23053
  public void testUnboundTypeVarsMatchClassObjectTypes() {
    doTest();
  }

  // PY-22513
  public void testGenericKwargs() {
    doTest();
  }

  public void testTypingNamedTupleAsParameter() {
    doTest();
  }

  // PY-17962
  public void testTypingCallableCall() {
    doTest();
  }

  // PY-23057
  public void testEllipsisInFunctionWithSpecifiedReturnType() {
    doTest();
  }

  // PY-23239, PY-23253
  public void testInitializingTypingNamedTuple() {
    doTest();
  }

  // PY-21302
  public void testInitializingNewType() {
    doTest();
  }

  // PY-21302
  public void testNewTypeAsParameter() {
    doTest();
  }

  // PY-21302
  public void testNewTypeInheritance() {
    doTest();
  }

  // PY-24287
  public void testPromotingBytearrayToBytes() {
    doTest();
  }

  // PY-25045
  public void testUnionOfIntAndFloatShouldBeConsideredAsDividable() {
    doTest();
  }

  // PY-23289
  // PY-23391
  // PY-24194
  // PY-24789
  public void testTypingSupports() {
    doTest();
  }

  // PY-25994
  public void testUnresolvedReceiverGeneric() {
    doTest();
  }

  public void testMatchingOpenFunctionCallTypesPy3() {
    doMultiFileTest();
  }

  public void testChainedComparisonsGenericMatching() {
    doTest();
  }

  // PY-27398
  public void testInitializingDataclass() {
    doMultiFileTest();
  }

  // PY-28442
  public void testDataclassClsCallType() {
    doMultiFileTest();
  }

  // PY-26354
  public void testInitializingAttrs() {
    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> doTestByText(
        """
          import attr
          import typing

          @attr.s
          class Weak1:
              x = attr.ib()
              y = attr.ib(default=0)
              z = attr.ib(default=attr.Factory(list))
             \s
          Weak1(1, "str", 2)


          @attr.s
          class Weak2:
              x = attr.ib()
             \s
              @x.default
              def __init_x__(self):
                  return 1
             \s
          Weak2("str")


          @attr.s
          class Strong:
              x = attr.ib(type=int)
              y = attr.ib(default=0, type=int)
              z = attr.ib(default=attr.Factory(list), type=typing.List[int])
             \s
          Strong(1, <warning descr="Expected type 'int', got 'str' instead">"str"</warning>, <warning descr="Expected type 'list[int]', got 'list[str]' instead">["str"]</warning>)"""
      )
    );
  }

  // PY-28957
  public void testDataclassesReplace() {
    doMultiFileTest();
  }

  // PY-28127 PY-31424
  public void testInitializingTypeVar() {
    doTest();
  }

  // PY-24832
  public void testAssignment() {
    doTest();
  }

  // PY-24832
  public void testReAssignment() {
    doTest();
  }

  // PY-24832
  public void testTypeCommentAssignment() {
    doTest();
  }

  // PY-24832
  public void testTypeDeclarationAndAssignment() {
    doTest();
  }

  // PY-24832
  public void testClassLevelAssignment() {
    doTest();
  }

  // PY-24832
  public void testNoTypeMismatchInAssignmentWithoutTypeAnnotation() {
    doTest();
  }

  // PY-35235
  public void testTypingLiteralStrings() {
    doTestByText("""
                   from typing_extensions import Literal

                   a: Literal["abc"] = undefined
                   b: Literal[b"abc"] = undefined

                   def foo1(p1: Literal["abc"]):
                       pass
                   foo1(a)
                   foo1(<warning descr="Expected type 'Literal[\\"abc\\"]', got 'Literal[b\\"abc\\"]' instead">b</warning>)

                   def foo2(p1: Literal[b"abc"]):
                       pass
                   foo2(<warning descr="Expected type 'Literal[b\\"abc\\"]', got 'Literal[\\"abc\\"]' instead">a</warning>)
                   foo2(b)

                   def foo3(p1: str):
                       pass
                   foo3(a)
                   foo3(<warning descr="Expected type 'str', got 'Literal[b\\"abc\\"]' instead">b</warning>)

                   def foo4(p1: bytes):
                       pass
                   foo4(<warning descr="Expected type 'bytes', got 'Literal[\\"abc\\"]' instead">a</warning>)
                   foo4(b)
                   """);
  }

  // PY-42418
  public void testParametrizedBuiltinCollectionsAndTheirTypingAliasesAreEquivalent() {
    doTest();
  }

  // PY-42418
  public void testParametrizedBuiltinTypeAndTypingTypeAreEquivalent() {
    doTest();
  }

  // PY-30747
  public void testPathlibPathMatchingOsPathLike() {
    doTestByText(
      """
        import pathlib
        import os

        def foo(p: pathlib.Path):
            with open(p) as file:
                pass

        p1: pathlib.Path
        p2: os.PathLike[bytes] = p1  # false negative, see PyTypeChecker.matchGenerics
        p3: os.PathLike[str] = p1"""
    );
  }

  // PY-41847
  public void testTypingAnnotatedType() {
    doTestByText("""
                   from typing import Annotated
                   A = Annotated[bool, 'Some constraint']
                   a: A = <warning descr="Expected type 'bool', got 'str' instead">'str'</warning>
                   b: A = True
                   c: Annotated[bool, 'Some constraint'] = <warning descr="Expected type 'bool', got 'str' instead">'str'</warning>
                   d: Annotated[str, 'Some constraint'] = 'str'
                   """);
  }

  // PY-41847
  public void testTypingAnnotatedTypeMultiFile() {
    doMultiFileTest();
  }

  // PY-43838
  public void testParameterizedClassAgainstType() {
    doTestByText("""
                   from typing import Type, Any, List

                   def my_function(param: Type[Any]):
                       pass

                   my_function(List[str])""");
  }

  // PY-43838
  public void testUnionAgainstType() {
    doTestByText("""
                   from typing import Type, Any, Union

                   def my_function(param: Type[Any]):
                       pass

                   my_function(Union[int, str])""");
  }

  // PY-44575
  public void testArgsCallableAgainstOneParameterCallable() {
    doTestByText("""
                   from typing import Any, Callable, Iterable, TypeVar
                   _T1 = TypeVar("_T1")
                   def mymap(c: Callable[[_T1], Any], i: Iterable[_T1]) -> Iterable[_T1]:
                     pass
                   def myfoo(*args: int) -> int:
                     pass
                   mymap(myfoo, [1, 2, 3])
                   """);
  }

  // PY-44974
  public void testBitwiseOrUnionNoneIntStrAssignList() {
    doTestByText("bar: None | int | str = <warning descr=\"Expected type 'None | int | str', got 'list[int]' instead\">[42]</warning>");
  }

  // PY-44974
  public void testParenthesizedBitwiseOrUnionOfUnionsAssignNone() {
    doTestByText("bar: int | ((list | dict) | (float | str)) = <warning descr=\"Expected type 'int | list | dict | float | str', got 'None' instead\">None</warning>");
  }

  // PY-44974
  public void testTypingAndTypesBitwiseOrUnionDifference() {
    doTestByText("""
                   from typing import Type
                   def foo(x: Type[int | str]):
                       pass
                   foo(<warning descr="Expected type 'Type[int | str]', got 'UnionType' instead">int | str</warning>)""");
  }

  // PY-44974
  public void testBitwiseOrUnionsAndOldStyleUnionsAreEquivalent() {
    doTest();
  }

  // PY-49935
  public void testParamSpecExample() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import ParamSpec

                   P = ParamSpec("P")


                   def changes_return_type_to_str(x: Callable[P, int]) -> Callable[P, str]: ...


                   def returns_int(a: str, b: bool) -> int:
                       return 42


                   changes_return_type_to_str(returns_int)("42", <warning descr="Expected type 'bool', got 'int' instead">42</warning>)""");
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethod() {
    doTestByText("""
                   from typing import TypeVar, Generic, Callable, ParamSpec

                   U = TypeVar("U")
                   P = ParamSpec("P")


                   class Y(Generic[U, P]):
                       f: Callable[P, U]
                       attr: U

                       def __init__(self, f: Callable[P, U], attr: U) -> None:
                           self.f = f
                           self.attr = attr


                   def a(q: int) -> str: ...


                   expr = Y(a, '1').f(<warning descr="Expected type 'int', got 'str' instead">"42"</warning>)
                   """);
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethodSeveralParameters() {
    doTestByText("""
                   from typing import TypeVar, Generic, Callable, ParamSpec

                   U = TypeVar("U")
                   P = ParamSpec("P")


                   class Y(Generic[U, P]):
                       f: Callable[P, U]
                       attr: U

                       def __init__(self, f: Callable[P, U], attr: U) -> None:
                           self.f = f
                           self.attr = attr


                   def a(q: int, s: str) -> str: ...


                   expr = Y(a, '1').f(42, <warning descr="Expected type 'str', got 'int' instead">42</warning>)
                   """);
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethodConcatenate() {
    doTestByText("""
                   from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate

                   U = TypeVar("U")
                   P = ParamSpec("P")


                   class Y(Generic[U, P]):
                       f: Callable[Concatenate[int, P], U]
                       attr: U

                       def __init__(self, f: Callable[Concatenate[int, P], U], attr: U) -> None:
                           self.f = f
                           self.attr = attr


                   def a(q: int, s: str, b: bool) -> str: ...


                   expr = Y(a, '1').f(42, <warning descr="Expected type 'str', got 'int' instead">42</warning>, <warning descr="Expected type 'bool', got 'int' instead">42</warning>)
                   """);
  }

  // PY-49935
  public void testParamSpecConcatenateAddThirdParameter() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


                   add(bar)("42", 42, <warning descr="Expected type 'bool', got 'int' instead">42</warning>)""");
  }

  // PY-49935
  public void testParamSpecConcatenateAddSecondParameter() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


                   add(bar)("42", <warning descr="Expected type 'int', got 'str' instead">"42"</warning>, True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateAddFirstParameter() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


                   add(bar)(<warning descr="Expected type 'str', got 'int' instead">42</warning>, 42, True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateAddFirstSeveralParameters() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def add(x: Callable[P, int]) -> Callable[Concatenate[str, list[str], P], bool]: ...


                   add(bar)(<warning descr="Expected type 'str', got 'int' instead">42</warning>, <warning descr="Expected type 'list[str]', got 'list[int]' instead">[42]</warning>, 3, True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateAddOk() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


                   add(bar)("42", 42, True, True, True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateRemove() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


                   remove(bar)(<warning descr="Expected type 'bool', got 'int' instead">42</warning>)""");
  }

  // PY-49935
  public void testParamSpecConcatenateRemoveOkOneBool() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


                   remove(bar)(True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateRemoveOkTwoBools() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


                   remove(bar)(True, True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateRemoveOkEmpty() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


                   remove(bar)()""");
  }

  // PY-49935
  public void testParamSpecConcatenateTransform() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def transform(
                           x: Callable[Concatenate[int, P], int]
                   ) -> Callable[Concatenate[str, P], bool]:
                       def inner(s: str, *args: P.args):
                           return True
                       return inner


                   transform(bar)(<warning descr="Expected type 'str', got 'int' instead">42</warning>)""");
  }

  // PY-50337
  public void testBitwiseOrUnionWithNotCalculatedGenericFromUnion() {
    doTestByText("""
                   from typing import Union, TypeVar

                   T = TypeVar("T", bytes, str)

                   my_union = Union[str, set[T]]
                   another_union = Union[list[str], my_union[T]]


                   def foo(path_or_buf: another_union[T] | None) -> None:
                       print(path_or_buf)
                   """);
  }

  // PY-50403
  public void testFunctionNamedParameterUnification() {
    doTestByText("""
                   from collections.abc import Callable
                   from typing import ParamSpec

                   P = ParamSpec("P")


                   def twice(f: Callable[P, int], *args: P.args, **kwargs: P.kwargs) -> int:
                       return f(*args, **kwargs) + f(*args, **kwargs)


                   def a_int_b_str(a: int, b: str) -> int:
                       return a + len(b)


                   res1 = twice(a_int_b_str, 1, "A")

                   res2 = twice(a_int_b_str, b="A", a=1)

                   res3 = twice(a_int_b_str, <warning descr="Expected type 'int', got 'str' instead">"A"</warning>, <warning descr="Expected type 'str', got 'int' instead">1</warning>)

                   res4 = twice(a_int_b_str, <warning descr="Expected type 'str', got 'int' instead">b=1</warning>, <warning descr="Expected type 'int', got 'str' instead">a="A"</warning>)""");
  }

  // PY-50403
  public void testFunctionNotEnoughArgumentsToMatchWithParamSpec() {
    doTestByText("""
                   from typing import ParamSpec, Callable, TypeVar

                   P = ParamSpec('P')
                   T = TypeVar('T')


                   def caller(f: Callable[P, T], *args: P.args, **kwargs: P.kwargs):
                       f(*args, **kwargs)


                   def func(n: int, s: str) -> None:
                       pass


                   caller(func, 42<warning descr="Parameter 's' unfilled (from ParamSpec 'P')">)</warning>
                   """);
  }

  // PY-50403
  public void testFunctionTooManyArgumentsToMatchWithParamSpec() {
    doTestByText("""
                   from typing import ParamSpec, Callable, TypeVar

                   P = ParamSpec('P')
                   T = TypeVar('T')


                   def caller(f: Callable[P, T], *args: P.args, **kwargs: P.kwargs):
                       f(*args, **kwargs)


                   def func(n: int, s: str) -> None:
                       pass


                   caller(func, 42, 'foo', <warning descr="Unexpected argument (from ParamSpec 'P')">None</warning>)""");
  }

  // PY-50403
  public void testFunctionNamedArgumentNotMatchWithParamSpec() {
    doTestByText("""
                   from typing import ParamSpec, Callable, TypeVar

                   P = ParamSpec('P')
                   T = TypeVar('T')


                   def caller(f: Callable[P, T], *args: P.args, **kwargs: P.kwargs):
                       f(*args, **kwargs)


                   def func(foo: int) -> None:
                       pass


                   caller(func, <warning descr="Unexpected argument (from ParamSpec 'P')">bar=42</warning><warning descr="Parameter 'foo' unfilled (from ParamSpec 'P')">)</warning>""");
  }

  // PY-50403
  public void testSameArgumentPassedTwiceInParamSpec() {
    doTestByText("""
                   from typing import ParamSpec, Callable, TypeVar

                   P = ParamSpec('P')
                   T = TypeVar('T')


                   def caller(f: Callable[P, T], *args: P.args, **kwargs: P.kwargs):
                       f(*args, **kwargs)


                   def func(n: int) -> None:
                       pass


                   caller(func, 42, <warning descr="Unexpected argument (from ParamSpec 'P')">n=42</warning>)""");
  }

  // PY-46661
  public void testTypedDictInReturnType() {
    doTest();
  }

  // PY-53611
  public void testTypedDictRequiredNotRequiredKeys() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing import TypedDict
                                              from typing_extensions import Required, NotRequired
                                              class WithTotalFalse(TypedDict, total=False):
                                                  x: Required[int]
                                              class WithTotalTrue(TypedDict, total=True):
                                                  x: NotRequired[int]
                                              class WithoutTotal(TypedDict):
                                                  x: NotRequired[int]
                                              class WithoutTotalWithExplicitRequired(TypedDict):
                                                  x: Required[int]
                                                  y: NotRequired[int]
                                              AlternativeSyntax = TypedDict("AlternativeSyntax", {'x': NotRequired[int]})
                                              with_total_false: WithTotalFalse = <warning descr="TypedDict 'WithTotalFalse' has missing key: 'x'">{}</warning>
                                              with_total_true: WithTotalTrue = {}
                                              without_total: WithoutTotal = {}
                                              without_total_with_explicit_required: WithoutTotalWithExplicitRequired = <warning descr="TypedDict 'WithoutTotalWithExplicitRequired' has missing key: 'x'">{}</warning>
                                              alternative_syntax: AlternativeSyntax = {}
                                              """));
  }

  // PY-53611
  public void testTypedDictRequiredNotRequiredEquivalence() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-53611
  public void testTypedDictRequiredNotRequiredMixedWithAnnotated() {
    runWithLanguageLevel(LanguageLevel.getLatest(),
                         () -> doTestByText("""
                                              from typing_extensions import TypedDict, Required, NotRequired, Annotated
                                              class A(TypedDict):
                                                  x: Annotated[NotRequired[int], 'Some constraint']
                                              def f(a: A):
                                                  pass
                                              f({})
                                              class B(TypedDict, total=False):
                                                  x: Annotated[Required[int], 'Some constraint']
                                              def g(b: B):
                                                  pass
                                              g(<warning descr="TypedDict 'B' has missing key: 'x'">{}</warning>)
                                              """));
  }

  // PY-53611
  public void testTypingRequiredTypeSpecificationsMultiFile() {
    doMultiFileTest();
  }

  // PY-52648 Requires PY-53896 or patching Typeshed
  public void testListLiteralPassedToIter() {
    doTestByText("iter([1, 2, 3])");
  }

  // PY-53104
  public void testParameterSelf() {
    doTestByText("""
                   from typing import Self, Callable

                   class Shape:
                       def difference(self, other: Self) -> float: ...

                       def apply(self, f: Callable[[Self], None]) -> None: ...


                   class Circle(Shape):
                       pass


                   def fCircle(c: Circle):
                     pass


                   def fShape(sh: Shape):
                     pass


                   sh = Shape()
                   cir = Circle()

                   sh.difference(cir)
                   sh.difference(sh)
                   cir.difference(cir)
                   cir.difference(<warning descr="Expected type 'Circle' (matched generic type 'Self'), got 'Shape' instead">sh</warning>)

                   cir.apply(fCircle)
                   cir.apply(<warning descr="Expected type '(Circle) -> None' (matched generic type '(Self) -> None'), got '(sh: Shape) -> None' instead">fShape</warning>)
                   sh.apply(fCircle)
                   sh.apply(fShape)""");
  }

  // PY-53104
  public void testParameterTypeSelf() {
    doTestByText("""
                   from typing import Self, Callable

                   class MyClass:
                       def foo(self, bar: Type[Self]) -> None: ...


                   class SubClass(MyClass):
                       pass


                   myClass = MyClass()
                   subClass = MySubClass()

                   myClass.foo(myClass)
                   myClass.foo(subClass)
                   myClass.foo(MyClass)
                   myClass.foo(SubClass)

                   subClass.foo(myClass)
                   subClass.foo(subClass)
                   subClass.foo(MyClass)
                   subClass.foo(SubClass)""");
  }

  // PY-53104
  public void testParameterTypeSelfUnion() {
    doTestByText("""
                   from typing import Self, Callable

                   class MyClass:
                       def foo(self, bar: Self | None | int) -> None: ...


                   class SubClass(MyClass):
                       pass


                   myClass = MyClass()
                   subClass = SubClass()

                   myClass.foo(myClass)
                   myClass.foo(subClass)
                   myClass.foo(42)
                   myClass.foo(None)
                   myClass.foo(<warning descr="Expected type 'MyClass | None | int' (matched generic type 'Self | None | int'), got 'str' instead">""</warning>)

                   subClass.foo(<warning descr="Expected type 'SubClass | None | int' (matched generic type 'Self | None | int'), got 'MyClass' instead">myClass</warning>)
                   subClass.foo(subClass)
                   subClass.foo(42)
                   subClass.foo(None)
                   subClass.foo(<warning descr="Expected type 'SubClass | None | int' (matched generic type 'Self | None | int'), got 'str' instead">""</warning>)""");
  }

  // PY-53104
  public void testParameterTypeSelfReturnAsParameter() {
    doTestByText("""
                   from typing import Self, Callable

                   class MyClass:
                       def foo(self, bar: Self) -> Self: ...


                   class SubClass(MyClass):
                       pass


                   myClass = MyClass()
                   subClass = SubClass()

                   myClass.foo(myClass.foo(myClass))
                   myClass.foo(subClass.foo(subClass))
                   myClass.foo(myClass.foo(subClass))
                   myClass.foo(subClass.foo(<warning descr="Expected type 'SubClass' (matched generic type 'Self'), got 'MyClass' instead">myClass</warning>))

                   subClass.foo(<warning descr="Expected type 'SubClass' (matched generic type 'Self'), got 'MyClass' instead">myClass.foo(myClass)</warning>)
                   subClass.foo(subClass.foo(subClass))
                   subClass.foo(<warning descr="Expected type 'SubClass' (matched generic type 'Self'), got 'MyClass' instead">myClass.foo(subClass)</warning>)
                   subClass.foo(subClass.foo(<warning descr="Expected type 'SubClass' (matched generic type 'Self'), got 'MyClass' instead">myClass</warning>))""");
  }

  // PY-53104
  public void testProtocolSelfClass() {
    doTestByText("""
                   from __future__ import annotations
                   from typing import Self, Protocol


                   class MyProtocol(Protocol):
                       def foo(self, bar: float) -> Self: ...


                   class MyClass:
                       def foo(self, bar: float) -> MyClass:
                           pass


                   def accepts_protocol(obj: MyProtocol) -> None:
                       print(obj)


                   obj = MyClass()
                   accepts_protocol(obj)
                   """);
  }

  // PY-53104
  public void testProtocolSelfSubclass() {
    doTestByText("""
                   from __future__ import annotations
                   from typing import Self, Protocol


                   class MyProtocol(Protocol):
                       def foo(self, bar: float) -> Self: ...


                   class MyClass:
                       def foo(self, bar: float) -> MySubClass:
                           pass


                   class MySubClass(MyClass):
                       pass


                   def accepts_protocol(obj: MyProtocol) -> None:
                       print(obj)


                   obj = MyClass()
                   accepts_protocol(obj)
                   """);
  }

  // PY-53104
  public void testProtocolSelfOtherClass() {
    doTestByText("""
                   from __future__ import annotations
                   from typing import Self, Protocol


                   class MyProtocol(Protocol):
                       def foo(self, bar: float) -> Self: ...


                   class MyClass:
                       def foo(self, bar: float) -> int:
                           pass


                   def accepts_protocol(obj: MyProtocol) -> None:
                       print(obj)


                   obj = MyClass()
                   accepts_protocol(<warning descr="Expected type 'MyProtocol', got 'MyClass' instead">obj</warning>)
                   """);
  }

  // PY-53104
  public void testProtocolSelfNotSubclass() {
    doTestByText("""
                   from __future__ import annotations
                   from typing import Self, Protocol


                   class MyProtocol(Protocol):
                       def foo(self, bar: float) -> Self: ...


                   class MyClass:
                       def foo(self, bar: float) -> MyClassNotSubclass:
                           pass


                   class MyClassNotSubclass:
                       def foo(self, bar: float) -> int:
                           pass


                   def accepts_protocol(obj: MyProtocol) -> None:
                       print(obj)


                   obj = MyClass()
                   accepts_protocol(<warning descr="Expected type 'MyProtocol', got 'MyClass' instead">obj</warning>)
                   """);
  }

  // PY-53104
  public void testProtocolSelfSelf() {
    doTestByText("""
                   from __future__ import annotations
                   from typing import Self, Protocol


                   class MyProtocol(Protocol):
                       def foo(self, bar: float) -> Self: ...


                   class MyClass:
                       def foo(self, bar: float) -> Self:
                           pass


                   def accepts_protocol(obj: MyProtocol) -> None:
                       print(obj)


                   obj = MyClass()
                   accepts_protocol(obj)
                   """);
  }

  // PY-56785
  public void testTypingSelfNoInspectionReturnSelfMethod() {
    doTestByText("""
                   from typing import Self


                   class Builder:
                       def foo(self) -> Self:
                           result = self.bar()
                           return result

                       def bar(self) -> Self:
                           pass
                   """);
  }

  // PY-56785
  public void testTypingSelfClassMethodReturnClsNoHighlighting() {
    doTestByText("""
                   from typing import Self

                   class Shape:

                       def __init__(self, scale: float):
                           self.scale = None

                       @classmethod
                       def from_config(cls, config: dict[str, float]) -> Self:
                           return cls(config["scale"])
                   """);
  }

  // PY-56785
  public void _testTypingSelfAndExplicitClassReturn() {
    doTestByText("""
                   from __future__ import annotations
                   from typing import Self

                   class SomeClass():
                       def foo(self, bar: Self) -> Self:
                           return <warning descr="Cannot return explicit class in self annotated function">SomeClass()</warning>
                   """);
  }

  // PY-56785
  public void _testTypingSelfReturnSubClassMethod() {
    doTestByText("""
                   from typing import Self


                   class Builder:
                       def foo(self) -> Self:
                           result = SubBuilder().bar()
                           return <warning descr="Cannot return explicit class in self annotated function">result</warning>

                       def bar(self) -> Self:
                           pass


                   class SubBuilder(Builder):
                       pass
                   """);
  }
}
