// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
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
        "import attr\n" +
        "import typing\n" +
        "\n" +
        "@attr.s\n" +
        "class Weak1:\n" +
        "    x = attr.ib()\n" +
        "    y = attr.ib(default=0)\n" +
        "    z = attr.ib(default=attr.Factory(list))\n" +
        "    \n" +
        "Weak1(1, \"str\", 2)\n" +
        "\n" +
        "\n" +
        "@attr.s\n" +
        "class Weak2:\n" +
        "    x = attr.ib()\n" +
        "    \n" +
        "    @x.default\n" +
        "    def __init_x__(self):\n" +
        "        return 1\n" +
        "    \n" +
        "Weak2(\"str\")\n" +
        "\n" +
        "\n" +
        "@attr.s\n" +
        "class Strong:\n" +
        "    x = attr.ib(type=int)\n" +
        "    y = attr.ib(default=0, type=int)\n" +
        "    z = attr.ib(default=attr.Factory(list), type=typing.List[int])\n" +
        "    \n" +
        "Strong(1, <warning descr=\"Expected type 'int', got 'str' instead\">\"str\"</warning>, <warning descr=\"Expected type 'list[int]', got 'list[str]' instead\">[\"str\"]</warning>)"
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
    doTestByText("from typing_extensions import Literal\n" +
                 "\n" +
                 "a: Literal[\"abc\"] = undefined\n" +
                 "b: Literal[b\"abc\"] = undefined\n" +
                 "\n" +
                 "def foo1(p1: Literal[\"abc\"]):\n" +
                 "    pass\n" +
                 "foo1(a)\n" +
                 "foo1(<warning descr=\"Expected type 'Literal[\\\"abc\\\"]', got 'Literal[b\\\"abc\\\"]' instead\">b</warning>)\n" +
                 "\n" +
                 "def foo2(p1: Literal[b\"abc\"]):\n" +
                 "    pass\n" +
                 "foo2(<warning descr=\"Expected type 'Literal[b\\\"abc\\\"]', got 'Literal[\\\"abc\\\"]' instead\">a</warning>)\n" +
                 "foo2(b)\n" +
                 "\n" +
                 "def foo3(p1: str):\n" +
                 "    pass\n" +
                 "foo3(a)\n" +
                 "foo3(<warning descr=\"Expected type 'str', got 'Literal[b\\\"abc\\\"]' instead\">b</warning>)\n" +
                 "\n" +
                 "def foo4(p1: bytes):\n" +
                 "    pass\n" +
                 "foo4(<warning descr=\"Expected type 'bytes', got 'Literal[\\\"abc\\\"]' instead\">a</warning>)\n" +
                 "foo4(b)\n");
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
      "import pathlib\n" +
      "import os\n" +
      "\n" +
      "def foo(p: pathlib.Path):\n" +
      "    with open(p) as file:\n" +
      "        pass\n" +
      "\n" +
      "p1: pathlib.Path\n" +
      "p2: os.PathLike[bytes] = p1  # false negative, see PyTypeChecker.matchGenerics\n" +
      "p3: os.PathLike[str] = p1"
    );
  }

  // PY-41847
  public void testTypingAnnotatedType() {
    doTestByText("from typing import Annotated\n" +
                 "A = Annotated[bool, 'Some constraint']\n" +
                 "a: A = <warning descr=\"Expected type 'bool', got 'str' instead\">'str'</warning>\n" +
                 "b: A = True\n" +
                 "c: Annotated[bool, 'Some constraint'] = <warning descr=\"Expected type 'bool', got 'str' instead\">'str'</warning>\n" +
                 "d: Annotated[str, 'Some constraint'] = 'str'\n");
  }

  // PY-41847
  public void testTypingAnnotatedTypeMultiFile() {
    doMultiFileTest();
  }

  // PY-43838
  public void testParameterizedClassAgainstType() {
    doTestByText("from typing import Type, Any, List\n" +
                 "\n" +
                 "def my_function(param: Type[Any]):\n" +
                 "    pass\n" +
                 "\n" +
                 "my_function(List[str])");
  }

  // PY-43838
  public void testUnionAgainstType() {
    doTestByText("from typing import Type, Any, Union\n" +
                 "\n" +
                 "def my_function(param: Type[Any]):\n" +
                 "    pass\n" +
                 "\n" +
                 "my_function(Union[int, str])");
  }

  // PY-44575
  public void testArgsCallableAgainstOneParameterCallable() {
    doTestByText("from typing import Any, Callable, Iterable, TypeVar\n" +
                 "_T1 = TypeVar(\"_T1\")\n" +
                 "def mymap(c: Callable[[_T1], Any], i: Iterable[_T1]) -> Iterable[_T1]:\n" +
                 "  pass\n" +
                 "def myfoo(*args: int) -> int:\n" +
                 "  pass\n" +
                 "mymap(myfoo, [1, 2, 3])\n");
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
    doTestByText("from typing import Type\n" +
                 "def foo(x: Type[int | str]):\n" +
                 "    pass\n" +
                 "foo(<warning descr=\"Expected type 'Type[int | str]', got 'UnionType' instead\">int | str</warning>)");
  }

  // PY-44974
  public void testBitwiseOrUnionsAndOldStyleUnionsAreEquivalent() {
    doTest();
  }

  // PY-49935
  public void testParamSpecExample() {
    doTestByText("from collections.abc import Callable\n" +
                 "from typing import ParamSpec\n" +
                 "\n" +
                 "P = ParamSpec(\"P\")\n" +
                 "\n" +
                 "\n" +
                 "def changes_return_type_to_str(x: Callable[P, int]) -> Callable[P, str]: ...\n" +
                 "\n" +
                 "\n" +
                 "def returns_int(a: str, b: bool) -> int:\n" +
                 "    return 42\n" +
                 "\n" +
                 "\n" +
                 "changes_return_type_to_str(returns_int)(\"42\", <warning descr=\"Expected type 'bool', got 'int' instead\">42</warning>)");
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethod() {
    doTestByText("from typing import TypeVar, Generic, Callable, ParamSpec\n" +
                 "\n" +
                 "U = TypeVar(\"U\")\n" +
                 "P = ParamSpec(\"P\")\n" +
                 "\n" +
                 "\n" +
                 "class Y(Generic[U, P]):\n" +
                 "    f: Callable[P, U]\n" +
                 "    attr: U\n" +
                 "\n" +
                 "    def __init__(self, f: Callable[P, U], attr: U) -> None:\n" +
                 "        self.f = f\n" +
                 "        self.attr = attr\n" +
                 "\n" +
                 "\n" +
                 "def a(q: int) -> str: ...\n" +
                 "\n" +
                 "\n" +
                 "expr = Y(a, '1').f(<warning descr=\"Expected type 'int', got 'str' instead\">\"42\"</warning>)\n");
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethodSeveralParameters() {
    doTestByText("from typing import TypeVar, Generic, Callable, ParamSpec\n" +
                 "\n" +
                 "U = TypeVar(\"U\")\n" +
                 "P = ParamSpec(\"P\")\n" +
                 "\n" +
                 "\n" +
                 "class Y(Generic[U, P]):\n" +
                 "    f: Callable[P, U]\n" +
                 "    attr: U\n" +
                 "\n" +
                 "    def __init__(self, f: Callable[P, U], attr: U) -> None:\n" +
                 "        self.f = f\n" +
                 "        self.attr = attr\n" +
                 "\n" +
                 "\n" +
                 "def a(q: int, s: str) -> str: ...\n" +
                 "\n" +
                 "\n" +
                 "expr = Y(a, '1').f(42, <warning descr=\"Expected type 'str', got 'int' instead\">42</warning>)\n");
  }

  // PY-49935
  public void testParamSpecUserGenericClassMethodConcatenate() {
    doTestByText("from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate\n" +
           "\n" +
           "U = TypeVar(\"U\")\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "class Y(Generic[U, P]):\n" +
           "    f: Callable[Concatenate[int, P], U]\n" +
           "    attr: U\n" +
           "\n" +
           "    def __init__(self, f: Callable[Concatenate[int, P], U], attr: U) -> None:\n" +
           "        self.f = f\n" +
           "        self.attr = attr\n" +
           "\n" +
           "\n" +
           "def a(q: int, s: str, b: bool) -> str: ...\n" +
           "\n" +
           "\n" +
           "expr = Y(a, '1').f(42, <warning descr=\"Expected type 'str', got 'int' instead\">42</warning>, <warning descr=\"Expected type 'bool', got 'int' instead\">42</warning>)\n");
  }

  // PY-49935
  public void testParamSpecConcatenateAddThirdParameter() {
    doTestByText("from collections.abc import Callable\n" +
           "from typing import Concatenate, ParamSpec\n" +
           "\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "def bar(x: int, *args: bool) -> int: ...\n" +
           "\n" +
           "\n" +
           "def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...\n" +
           "\n" +
           "\n" +
           "add(bar)(\"42\", 42, <warning descr=\"Expected type 'bool', got 'int' instead\">42</warning>)");
  }

  // PY-49935
  public void testParamSpecConcatenateAddSecondParameter() {
    doTestByText("from collections.abc import Callable\n" +
                 "from typing import Concatenate, ParamSpec\n" +
                 "\n" +
                 "P = ParamSpec(\"P\")\n" +
                 "\n" +
                 "\n" +
                 "def bar(x: int, *args: bool) -> int: ...\n" +
                 "\n" +
                 "\n" +
                 "def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...\n" +
                 "\n" +
                 "\n" +
                 "add(bar)(\"42\", <warning descr=\"Expected type 'int', got 'str' instead\">\"42\"</warning>, True)");
  }

  // PY-49935
  public void testParamSpecConcatenateAddFirstParameter() {
    doTestByText("from collections.abc import Callable\n" +
                 "from typing import Concatenate, ParamSpec\n" +
                 "\n" +
                 "P = ParamSpec(\"P\")\n" +
                 "\n" +
                 "\n" +
                 "def bar(x: int, *args: bool) -> int: ...\n" +
                 "\n" +
                 "\n" +
                 "def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...\n" +
                 "\n" +
                 "\n" +
                 "add(bar)(<warning descr=\"Expected type 'str', got 'int' instead\">42</warning>, 42, True)");
  }

  // PY-49935
  public void testParamSpecConcatenateAddFirstSeveralParameters() {
    doTestByText("from collections.abc import Callable\n" +
                 "from typing import Concatenate, ParamSpec\n" +
                 "\n" +
                 "P = ParamSpec(\"P\")\n" +
                 "\n" +
                 "\n" +
                 "def bar(x: int, *args: bool) -> int: ...\n" +
                 "\n" +
                 "\n" +
                 "def add(x: Callable[P, int]) -> Callable[Concatenate[str, list[str], P], bool]: ...\n" +
                 "\n" +
                 "\n" +
                 "add(bar)(<warning descr=\"Expected type 'str', got 'int' instead\">42</warning>, <warning descr=\"Expected type 'list[str]', got 'list[int]' instead\">[42]</warning>, 3, True)");
  }

  // PY-49935
  public void testParamSpecConcatenateAddOk() {
    doTestByText("from collections.abc import Callable\n" +
                 "from typing import Concatenate, ParamSpec\n" +
                 "\n" +
                 "P = ParamSpec(\"P\")\n" +
                 "\n" +
                 "\n" +
                 "def bar(x: int, *args: bool) -> int: ...\n" +
                 "\n" +
                 "\n" +
                 "def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...\n" +
                 "\n" +
                 "\n" +
                 "add(bar)(\"42\", 42, True, True, True)");
  }

  // PY-49935
  public void testParamSpecConcatenateRemove() {
    doTestByText("from collections.abc import Callable\n" +
           "from typing import Concatenate, ParamSpec\n" +
           "\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "def bar(x: int, *args: bool) -> int: ...\n" +
           "\n" +
           "\n" +
           "def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...\n" +
           "\n" +
           "\n" +
           "remove(bar)(<warning descr=\"Expected type 'bool', got 'int' instead\">42</warning>)");
  }

  // PY-49935
  public void testParamSpecConcatenateRemoveOkOneBool() {
    doTestByText("from collections.abc import Callable\n" +
                 "from typing import Concatenate, ParamSpec\n" +
                 "\n" +
                 "P = ParamSpec(\"P\")\n" +
                 "\n" +
                 "\n" +
                 "def bar(x: int, *args: bool) -> int: ...\n" +
                 "\n" +
                 "\n" +
                 "def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...\n" +
                 "\n" +
                 "\n" +
                 "remove(bar)(True)");
  }

  // PY-49935
  public void testParamSpecConcatenateRemoveOkTwoBools() {
    doTestByText("from collections.abc import Callable\n" +
                 "from typing import Concatenate, ParamSpec\n" +
                 "\n" +
                 "P = ParamSpec(\"P\")\n" +
                 "\n" +
                 "\n" +
                 "def bar(x: int, *args: bool) -> int: ...\n" +
                 "\n" +
                 "\n" +
                 "def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...\n" +
                 "\n" +
                 "\n" +
                 "remove(bar)(True, True)");
  }

  // PY-49935
  public void testParamSpecConcatenateRemoveOkEmpty() {
    doTestByText("from collections.abc import Callable\n" +
                 "from typing import Concatenate, ParamSpec\n" +
                 "\n" +
                 "P = ParamSpec(\"P\")\n" +
                 "\n" +
                 "\n" +
                 "def bar(x: int, *args: bool) -> int: ...\n" +
                 "\n" +
                 "\n" +
                 "def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...\n" +
                 "\n" +
                 "\n" +
                 "remove(bar)()");
  }

  // PY-49935
  public void testParamSpecConcatenateTransform() {
    doTestByText("from collections.abc import Callable\n" +
           "from typing import Concatenate, ParamSpec\n" +
           "\n" +
           "P = ParamSpec(\"P\")\n" +
           "\n" +
           "\n" +
           "def bar(x: int, *args: bool) -> int: ...\n" +
           "\n" +
           "\n" +
           "def transform(\n" +
           "        x: Callable[Concatenate[int, P], int]\n" +
           ") -> Callable[Concatenate[str, P], bool]:\n" +
           "    def inner(s: str, *args: P.args):\n" +
           "        return True\n" +
           "    return inner\n" +
           "\n" +
           "\n" +
           "transform(bar)(<warning descr=\"Expected type 'str', got 'int' instead\">42</warning>)");
  }

  // PY-50337
  public void testBitwiseOrUnionWithNotCalculatedGenericFromUnion() {
    doTestByText("from typing import Union, TypeVar\n" +
                 "\n" +
                 "T = TypeVar(\"T\", bytes, str)\n" +
                 "\n" +
                 "my_union = Union[str, set[T]]\n" +
                 "another_union = Union[list[str], my_union[T]]\n" +
                 "\n" +
                 "\n" +
                 "def foo(path_or_buf: another_union[T] | None) -> None:\n" +
                 "    print(path_or_buf)\n");
  }

  // PY-46661
  public void testTypedDictInReturnType() {
    doTest();
  }

  // PY-52648
  public void testListLiteralPassedToIter() {
    doTest();
  }
}
