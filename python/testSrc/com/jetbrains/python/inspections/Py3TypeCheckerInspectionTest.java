// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

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
  
  public void testFunctionYieldTypePy3() {
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
          Weak1(1, <warning descr="Expected type 'int', got 'str' instead">"str"</warning>, <warning descr="Expected type 'list', got 'int' instead">2</warning>)


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

  // PY-36889
  public void testInstanceAndClassAttributeAssignment() {
    doTestByText("""
                   from typing import ClassVar
                   
                   class ClassAnnotations:
                       attr: int
                       class_attr: ClassVar[int]
                   
                   ClassAnnotations().attr = <warning descr="Expected type 'int', got 'str' instead">"foo"</warning>
                   ClassAnnotations.class_attr = <warning descr="Expected type 'int', got 'str' instead">"foo"</warning>
                   
                   class ClassAnnotationInstanceAssignment:
                       attr: int
                       def __init__(self, x):
                           self.attr = x
                   
                   ClassAnnotationInstanceAssignment(42).attr = <warning descr="Expected type 'int', got 'str' instead">"foo"</warning>
                   
                   class InstanceAnnotationAndAssignment:
                       def __init__(self):
                           self.attr: int = 42
                   
                   InstanceAnnotationAndAssignment().attr = <warning descr="Expected type 'int', got 'str' instead">"foo"</warning>
                   """);
  }

  // PY-36889
  public void testDataclassInstanceAssignment() {
    doTestByText("""
                   from dataclasses import dataclass
                   
                   @dataclass
                   class C:
                       attr: int
                   
                   C().attr = <warning descr="Expected type 'int', got 'str' instead">"foo"</warning>
                   """);
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

  // PY-46385
  public void testAliasingEnumClassNameInLiteralType() {
    doTestByText("""
             from enum import Enum
             from typing import Literal
     
             class Colors(Enum):
                 RED = 1
                 GREEN = 1
                 BLUE = 3
     
             AliasColors = Colors
     
             x: AliasColors = Colors.RED
             y: Literal[Colors.RED] = <warning descr="Expected type 'Literal[Colors.RED]', got 'Literal[Colors.GREEN]' instead">Colors.GREEN</warning>
             z: Literal[AliasColors.RED] = Colors.RED""");
  }

  // PY-46385
  public void testAliasingEnumMemberNameInLiteralType() {
    doTestByText("""
                   from enum import Enum
                   from typing import Literal
                   
                   class Colors(Enum):
                       RED = 1
                       GREEN = 2
                       BLUE = 3
                   
                   SpecialColors = Literal[Colors.RED]
                   
                   def special_painter(color: SpecialColors):
                       assert color == Colors.RED
                   
                   special_painter(<warning descr="Expected type 'Literal[Colors.RED]', got 'Literal[Colors.GREEN]' instead">Colors.GREEN</warning>)
                   
                   costs: dict[SpecialColors, int] = <warning descr="Expected type 'dict[Literal[Colors.RED], int]', got 'dict[Literal[Colors.GREEN], Literal[7]]' instead">{Colors.GREEN: 7}</warning>""");
  }

  public void testEnumMemberAlias() {
    doTestByText(
      """
        from enum import Enum
        from typing import Literal
        
        class Color(Enum):
            RED = 1
            R = RED
        
        x: Literal[Color.RED]
        x = Color.R"""
    );
  }

  public void testTypeNarrowingIsOrEquals() {
    doTestByText(
      """
        from enum import Enum
        from typing import Literal

        class Color(Enum):
            R = 1
            G = 2
            B = 3
            RED = R
            GREEN = G
            BLUE = B

        def foo(v: Color | str) -> None:
            if v is Color.RED:
                r: Literal[Color.R] = v
            elif v == Color.G:
                g: Literal[Color.G] = v
            elif v is Color.B:
                b: Literal[Color.B] = v
            else:
                s: str = v
                c: Color = <warning descr="Expected type 'Color', got 'str' instead">v</warning>
        
            if v is Color.BLUE or isinstance(v, str):
                pass
            else:
                s: str = <warning descr="Expected type 'str', got 'Literal[Color.R, Color.G]' instead">v</warning>

        def bar(v: Literal[Color.R, "1"]) -> None:
            if isinstance(v, Color):
                r: Literal[Color.R] = v
            else:
                s: Literal["1"] = v
                c: Color = <warning descr="Expected type 'Color', got 'Literal[\\"1\\"]' instead">v</warning>
        
        def buz(v: Color):
            if v is not Color.B and v != Color.RED:
                g: Literal[Color.G] = v
                s: str = <warning descr="Expected type 'str', got 'Literal[Color.G]' instead">v</warning>
        """
    );
  }

  // PY-79164
  public void testTypeNarrowingIn() {
    doTestByText("""
                   from typing import Literal
                   
                   def expects_bad_status(status: Literal["MALFORMED", "ABORTED"]): ...
                   
                   def expects_pending_status(status: Literal["PENDING"]): ...
                   
                   def parse_status(status: str) -> None:
                       if status in ("MALFORMED", "ABORTED"):
                           return expects_bad_status(status)
                   
                       if status == "PENDING":
                           expects_pending_status(status)
                   """);
    doTestByText("""
                   from typing import Literal
                   from enum import Enum
                   
                   class Color(Enum):
                       R = 1
                       G == 2
                       B == 3
                       RED = R
                       BLUE = B
                   
                   def expects_red_or_blue(v: Literal[Color.RED, Color.B]): ...
                   
                   def expects_green(v: Literal[Color.G]): ...
                   
                   def foo(v: Color):
                       if v in (Color.R, Color.BLUE):
                           expects_red_or_blue(v)
                       else:
                           expects_green(v)
                   
                       if v not in (Color.R, Color.BLUE):
                           expects_green(v)
                       else:
                           expects_red_or_blue(v)
                   """);

    doTestByText("""
                   from enum import Enum
                   
                   class MyEnum(Enum):
                       A = 1
                       B = 2
                       C = 3
                       D = 4
                   
                   def foo(v: MyEnum):
                       if v == MyEnum.A:
                           pass
                       elif v in (MyEnum.B, MyEnum.C):
                           b_or_c: Literal[MyEnum.B, MyEnum.C] = v
                           s: int = <warning descr="Expected type 'int', got 'Literal[MyEnum.B, MyEnum.C]' instead">v</warning>
                       else:
                           d: Literal[MyEnum.D] = v
                           s: int = <warning descr="Expected type 'int', got 'Literal[MyEnum.D]' instead">v</warning>
                   """);
  }

  // PY-77937
  public void testListOfEnumMembers() {
    doTestByText(
      """
        from enum import Enum
        
        class Direction(Enum):
            NORTH = "N"
            SOUTH = "S"
            EAST = "E"
            WEST = "W"
            LEFT = "L"
            RIGHT = "R"
            FORWARD = "F"
        
        CARTESIAN = [Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST]
        
        def index(d: Direction) -> None:
            print(CARTESIAN.index(d))
        """
    );
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
        p2: os.PathLike[bytes] = <warning descr="Expected type 'PathLike[bytes]', got 'Path' instead">p1</warning>
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

  public void testCallableWithTypeGuards() {
    doTestByText("""
                   from typing import Any, Callable
                   from typing_extensions import TypeIs
                   
                   def foo(c: Callable[[Any], TypeIs[int]]):
                      ...
                   
                   def is_str(x: Any) -> TypeIs[str]:
                      ...
                   
                   foo(<warning descr="Expected type '(Any) -> TypeIs[int]', got '(x: Any) -> TypeIs[str]' instead">is_str</warning>)
                   """);
  }

  public void testCallableWithTypeGuards2() {
    doTestByText("""
                   from typing import Any, Callable
                   from typing_extensions import TypeIs
                   
                   def foo(c: Callable[[Any], TypeIs[str]]):
                      ...
                   
                   def is_str(x: Any) -> TypeIs[str]:
                      ...
                   
                   foo(is_str)
                   """);
  }

  public void testCallableWithTypeGuards3() {
    doTestByText("""
                   from typing import Any, Callable
                   from typing_extensions import TypeIs
                   
                   class B:
                      ...
                   
                   class D(B):
                      ...
                   
                   def foo(c: Callable[[Any], TypeIs[D]]):
                      ...
                   
                   def is_str(x: Any) -> TypeIs[B]:
                      ...
                   
                   foo(<warning descr="Expected type '(Any) -> TypeIs[D]', got '(x: Any) -> TypeIs[B]' instead">is_str</warning>)
                   """);
  }

  public void testCallableWithTypeGuards4() {
    doTestByText("""
                   from typing import Any, Callable
                   from typing_extensions import TypeIs
                   
                   class B:
                      ...
                   
                   class D(B):
                      ...
                   
                   def foo(c: Callable[[Any], TypeIs[B]]):
                      ...
                   
                   def is_str(x: Any) -> TypeIs[D]:
                      ...
                   foo(<warning descr="Expected type '(Any) -> TypeIs[B]', got '(x: Any) -> TypeIs[D]' instead">is_str</warning>)
                   """);
  }

  public void testTypeIsAndTypeGuardAreNotAssignableToEachOther() {
    doTestByText("""
                   from typing import Callable, TypeGuard, TypeIs
                   
                   def takes_typeguard(f: Callable[[object], TypeGuard[int]]) -> None:
                       pass
                   
                   def takes_typeis(f: Callable[[object], TypeIs[int]]) -> None:
                       pass
                   
                   def is_int_typeis(val: object) -> TypeIs[int]:
                       return isinstance(val, int)
                   
                   def is_int_typeguard(val: object) -> TypeGuard[int]:
                       return isinstance(val, int)
                   
                   takes_typeguard(is_int_typeguard)
                   takes_typeguard(<warning descr="Expected type '(object) -> TypeGuard[int]', got '(val: object) -> TypeIs[int]' instead">is_int_typeis</warning>)
                   takes_typeis(<warning descr="Expected type '(object) -> TypeIs[int]', got '(val: object) -> TypeGuard[int]' instead">is_int_typeguard</warning>)
                   takes_typeis(is_int_typeis)
                   """);
  }

  // test is broken, type variable in TypeIs is invariant, so TypeIs[B] and TypeIs[D] are not consistent
  public void testCallableBoolean() {
    doTestByText("""
                   from typing import Any, Callable
                   from typing_extensions import TypeIs
                   
                   def foo(c: bool):
                      ...
                   
                   def is_int(x: Any) -> TypeIs[int]:
                      ...
                   
                   foo(is_int(1))
                   """);
  }

  public void testCallableStr() {
    doTestByText("""
                   from typing import Any, Callable
                   from typing_extensions import TypeIs
                   
                   def foo(c: str):
                      ...
                   
                   def is_int(x: Any) -> TypeIs[int]:
                      ...
                   
                   foo(<warning descr="Expected type 'str', got 'TypeIs[int]' instead">is_int(1)</warning>)
                   """);
  }

  public void testCallableInt() {
    doTestByText("""
                   from typing import Any, Callable
                   from typing_extensions import TypeIs
                   
                   def foo(c: int):
                      ...
                   
                   def is_int(x: Any) -> TypeIs[int]:
                      ...
                   
                   foo(is_int(1))
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
                   from typing import Callable, ParamSpec

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
                   from typing import Callable, Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


                   add(bar)("42", 42, <warning descr="Expected type 'bool', got 'int' instead">42</warning>)""");
  }

  // PY-49935
  public void testParamSpecConcatenateAddSecondParameter() {
    doTestByText("""
                   from typing import Callable,  Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


                   add(bar)("42", <warning descr="Expected type 'int', got 'str' instead">"42"</warning>, True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateAddFirstParameter() {
    doTestByText("""
                   from typing import Callable,  Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


                   add(bar)(<warning descr="Expected type 'str', got 'int' instead">42</warning>, 42, True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateAddFirstSeveralParameters() {
    doTestByText("""
                   from typing import Callable,  Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def add(x: Callable[P, int]) -> Callable[Concatenate[str, list[str], P], bool]: ...


                   add(bar)(<warning descr="Expected type 'str', got 'int' instead">42</warning>, <warning descr="Expected type 'list[str]', got 'list[int]' instead">[42]</warning>, 3, True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateAddOk() {
    doTestByText("""
                   from typing import Callable,  Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


                   add(bar)("42", 42, True, True, True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateRemove() {
    doTestByText("""
                   from typing import Callable,  Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


                   remove(bar)(<warning descr="Expected type 'bool', got 'int' instead">42</warning>)""");
  }

  // PY-49935
  public void testParamSpecConcatenateRemoveOkOneBool() {
    doTestByText("""
                   from typing import Callable,  Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


                   remove(bar)(True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateRemoveOkTwoBools() {
    doTestByText("""
                   from typing import Callable,  Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


                   remove(bar)(True, True)""");
  }

  // PY-49935
  public void testParamSpecConcatenateRemoveOkEmpty() {
    doTestByText("""
                   from typing import Callable,  Concatenate, ParamSpec

                   P = ParamSpec("P")


                   def bar(x: int, *args: bool) -> int: ...


                   def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


                   remove(bar)()""");
  }

  // PY-49935
  public void testParamSpecConcatenateTransform() {
    doTestByText("""
                   from typing import Callable,  Concatenate, ParamSpec

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

  // PY-79098
  public void testCallableConcatenateMatching() {
    doTestByText("""
                   from typing import Callable, Concatenate, reveal_type
                   
                   def f[**P2](fn: Callable[Concatenate[int, int, P2], None]) -> <warning descr="Expected type '(**P2) -> None', got 'None' instead">Callable[P2, None]</warning>:
                       def shorter_concat[**P3](fn: Callable[Concatenate[int, P3], None]):
                           f(<warning descr="Expected type '(Concatenate(int, int, **P2)) -> None' (matched generic type '(Concatenate(int, int, **P2)) -> None'), got '(Concatenate(int, **P3)) -> None' instead">fn</warning>)
                       def longer_concat[**P3](fn: Callable[Concatenate[int, int, int, P3], None]):
                           f(fn)
                       def empty(fn: Callable[[], None]):
                           f(<warning descr="Expected type '(Concatenate(int, int, **P2)) -> None' (matched generic type '(Concatenate(int, int, **P2)) -> None'), got '() -> None' instead">fn</warning>)
                       def param_spec[**P3](fn: Callable[P3, None]):
                           f(<warning descr="Expected type '(Concatenate(int, int, **P2)) -> None' (matched generic type '(Concatenate(int, int, **P2)) -> None'), got '(**P3) -> None' instead">fn</warning>)
                       def shorter_param_list[**P3](fn: Callable[[int], None]):
                           f(<warning descr="Expected type '(Concatenate(int, int, **P2)) -> None' (matched generic type '(Concatenate(int, int, **P2)) -> None'), got '(int) -> None' instead">fn</warning>)
                       def exact_param_list[**P3](fn: Callable[[int, int], None]):
                           f(fn)
                       def longer_param_list[**P3](fn: Callable[[int, int, int], None]):
                           f(fn)
                   """);
  }

  // PY-79098
  public void testUserGenericConcatenateMatching() {
    doTestByText("""
                   from typing import Callable, Concatenate, reveal_type
                   
                   class MyCallable[**P, R]:
                       pass
                   
                   def g[**P2](fn: MyCallable[Concatenate[int, int, P2], None]) -> <warning descr="Expected type 'MyCallable[**P2, None]', got 'None' instead">MyCallable[P2, None]</warning>:
                       def shorter_concat[**P3](fn: MyCallable[Concatenate[int, P3], None]):
                           g(<warning descr="Expected type 'MyCallable[Concatenate(int, int, **P2), None]' (matched generic type 'MyCallable[Concatenate(int, int, **P2), None]'), got 'MyCallable[Concatenate(int, **P3), None]' instead">fn</warning>)
                       def longer_concat[**P3](fn: MyCallable[Concatenate[int, int, int, P3], None]):
                           g(fn)
                       def empty(fn: MyCallable[[], None]):
                           g(<warning descr="Expected type 'MyCallable[Concatenate(int, int, **P2), None]' (matched generic type 'MyCallable[Concatenate(int, int, **P2), None]'), got 'MyCallable[[], None]' instead">fn</warning>)
                       def param_spec[**P3](fn: MyCallable[P3, None]):
                           g(<warning descr="Expected type 'MyCallable[Concatenate(int, int, **P2), None]' (matched generic type 'MyCallable[Concatenate(int, int, **P2), None]'), got 'MyCallable[**P3, None]' instead">fn</warning>)
                       def shorter_param_list[**P3](fn: MyCallable[[int], None]):
                           g(<warning descr="Expected type 'MyCallable[Concatenate(int, int, **P2), None]' (matched generic type 'MyCallable[Concatenate(int, int, **P2), None]'), got 'MyCallable[[int], None]' instead">fn</warning>)
                       def exact_param_list[**P3](fn: MyCallable[[int, int], None]):
                           g(fn)
                       def longer_param_list[**P3](fn: MyCallable[[int, int, int], None]):
                           g(fn)
                   """);
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
                   from typing import Callable,  ParamSpec

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

  // PY-38897
  public void testDictItemsAndIterableMatches() {
    doTestByText("""
                   from typing import Iterable, Tuple
                   
                   def foo(bar: Iterable[Tuple[str, int]]):
                       pass
                   
                   if __name__ == '__main__':
                       bar_dict = {'abc': 42}
                       foo(bar_dict.items())
                   """);
  }

  // PY-38897
  public void testDictItemsAndIterableMatchesGeneric() {
    doTestByText("""
                   from typing import Tuple
                   
                   def make_dict() -> dict[int, str]:
                       ...
                   
                   def key_func(param: Tuple[int, str]) -> int:
                       ...
                   
                   def foo() -> None:
                       my_dict = make_dict()
                       items = my_dict.items()
                       print(max(items, key=key_func))
                   """);
  }

  public void testClassConstructorTypeParameterDefinedOnInheritance() {
    doTestByText("""
                   from typing import Generic, TypeVar
                   
                   T = TypeVar('T')
                   
                   class Box(Generic[T]):
                       def __init__(self, value: T) -> None:
                           pass
                   
                   class StrBox(Box[str]):
                       pass
                   
                   StrBox(<warning descr="Expected type 'str' (matched generic type 'T'), got 'int' instead">42</warning>)
                   """);
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

  // PY-78126
  public void testTypedDictVariableKey() {
    doTestByText("""
                   from typing import TypedDict, Literal
                   class Movie(TypedDict):
                       name: str
                       year: int
                   def foo(key: str):
                       m: Movie = <warning descr="Expected type 'Movie', got 'dict[str, str | int]' instead">{key: "abb", "year": 1917}</warning>
                   def bar(key: Literal["name"]):
                       m: Movie = {key: "abb", "year": 1917} # OK
                   def buz(key: Literal["wrong_key"]):
                       m: Movie = <warning descr="TypedDict 'Movie' has missing key: 'name'">{<warning descr="Extra key 'wrong_key' for TypedDict 'Movie'">key: "abb"</warning>, "year": 1917}</warning>
                   """);
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

  public void testRequiredWithReadOnly() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> doTestByText(
      """
        from typing_extensions import TypedDict, Required, NotRequired, ReadOnly
        
        class Movie(TypedDict):
            name: ReadOnly[Required[str]]
            year: NotRequired[int]
        
        m: Movie = <warning descr="TypedDict 'Movie' has missing key: 'name'">{"year": 2024}</warning>
        """
    ));
  }

  public void testTypedDictsReadonlyConsistency() {
    doTestByText("""
                   from typing import TypedDict, Required, NotRequired, ReadOnly
                   
                   class A1(TypedDict):
                       x: NotRequired[str]
                   
                   class B1(TypedDict):
                       x: NotRequired[ReadOnly[str]]
                   
                   class B2(TypedDict):
                       x: ReadOnly[NotRequired[str]]
                   
                   class C(TypedDict):
                       x: Required[str]
                   
                   def func1(b1: B1, b2: B2, c: C):
                       v1: A1 = <warning descr="Expected type 'A1', got 'B1' instead">b1</warning>
                       v2: A1 = <warning descr="Expected type 'A1', got 'B2' instead">b2</warning>
                       v3: B1 = c
                   
                   class A2(TypedDict):
                       x: ReadOnly[NotRequired[object]]
                   
                   class B3(TypedDict):
                       pass
                   
                   def func2(b: B3):
                       a: A2 = b
                   """);
  }

  // PY-53611
  public void testTypingRequiredTypeSpecificationsMultiFile() {
    doMultiFileTest();
  }

  // PY-52648
  public void testListLiteralPassedToIter() {
    doTestByText("iter([1, 2, 3])");
  }

  // PY-52648
  public void testListLiteralPassedToIterSimplified() {
    doTest();
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

  // PY-53105
  public void testVariadicGenericInFunction() {
    doTestByText("""
                   from typing import Tuple, TypeVarTuple, TypeVar

                   T = TypeVar('T')
                   Ts = TypeVarTuple('Ts')


                   def foo(x: T, y: Tuple[*Ts]):
                       pass


                   foo(10, (1, '1', [1]))
                   """);
  }

  // PY-53105
  public void testVariadicGenericArgumentByCallableInFunction() {
    doTestByText("""
                   from typing import Callable, TypeVarTuple, Tuple

                   Ts = TypeVarTuple('Ts')


                   def foo(a: int, f: Callable[[*Ts], None], args: Tuple[*Ts]) -> None: ...
                   def bar(a: int, b: str) -> None: ...


                   foo(1, bar, args=(0, 'foo'))

                   foo(1, bar, <warning descr="Expected type 'tuple[int, str]' (matched generic type 'tuple[*Ts]'), got 'tuple[str, int]' instead">args=('foo', 0)</warning>)
                   """);
  }

  // PY-53105
  public void testVariadicGenericCheckCallableInFunction() {
    doTestByText("""
                   from typing import TypeVar, TypeVarTuple, Callable, Tuple

                   T = TypeVar('T')
                   Ts = TypeVarTuple('Ts')


                   def foo(f: Callable[[int, *Ts, T], Tuple[T, *Ts]]) -> None: ...


                   def ok1(a: int, b: str, c: bool, d: list[int]) -> Tuple[list[int], str, bool]: ...
                   def ok2(a: int, b: str) -> Tuple[str]: ...


                   foo(ok1)
                   foo(ok2)


                   def err1(a: int, b: str, c: bool, d: list[int]) -> Tuple[list[int], str, str]: ...
                   def err2(a: int, b: str) -> Tuple[str, str]: ...


                   foo(<warning descr="Expected type '(int, str, bool, list[int]) -> tuple[list[int], str, bool]' (matched generic type '(int, *Ts, T) -> tuple[T, *Ts]'), got '(a: int, b: str, c: bool, d: list[int]) -> tuple[list[int], str, str]' instead">err1</warning>)
                   foo(<warning descr="Expected type '(int, str) -> tuple[str]' (matched generic type '(int, *Ts, T) -> tuple[T, *Ts]'), got '(a: int, b: str) -> tuple[str, str]' instead">err2</warning>)
                   """);
  }

  // PY-53105
  public void testVariadicGenericTwoTsInFunction() {
    doTestByText("""
                   from typing import TypeVarTuple, Generic

                   Ts = TypeVarTuple('Ts')


                   class Array(Generic[*Ts]):
                       ...


                   def foo(x: Array[*Ts], y: Array[*Ts]) -> Array[*Ts]:
                       ...


                   x: Array[int]
                   y: Array[str]
                   z: Array[int, str]

                   foo(x, x)

                   foo(x, <warning descr="Expected type 'Array[int]' (matched generic type 'Array[*Ts]'), got 'Array[str]' instead">y</warning>)
                   foo(x, <warning descr="Expected type 'Array[int]' (matched generic type 'Array[*Ts]'), got 'Array[int, str]' instead">z</warning>)
                   """);
  }

  // PY-53105
  public void testVariadicGenericUnboundTupleInFunction() {
    doTestByText("""
                   from typing import Generic, TypeVarTuple, Tuple, Any
                                      
                   Ts = TypeVarTuple('Ts')
                                      
                                      
                   class Array(Generic[*Ts]):
                       def __init__(self, shape: Tuple[*Ts]):
                           ...
                                      
                                      
                   def foo(x: Array[int, *Tuple[Any, ...], str]) -> None:
                       ...
                                      
                                      
                   x: Array[int, list[str], bool, str]
                   foo(x)
                                      
                   y: Array[int, str]
                   foo(y)
                                      
                   z: Array[int]
                   foo(<warning descr="Expected type 'Array[int, *tuple[Any, ...], str]', got 'Array[int]' instead">z</warning>)
                                      
                   t: Array[str]
                   foo(<warning descr="Expected type 'Array[int, *tuple[Any, ...], str]', got 'Array[str]' instead">t</warning>)
                                      
                   k: Array[int, int]
                   foo(<warning descr="Expected type 'Array[int, *tuple[Any, ...], str]', got 'Array[int, int]' instead">k</warning>)
                   """);
  }

  // PY-53105
  public void testVariadicGenericStarArgsNamedParameters() {
    doTestByText("""
                   from typing import Tuple, TypeVarTuple
                                      
                   Ts = TypeVarTuple('Ts')
                                      
                                      
                   def foo(a: str, *args: *Tuple[*Ts, int], b: str, c: bool) -> None: ...
                                      
                                      
                   foo('', 1, True, [1], 42, b='', c=True)
                   foo('', 42, b='', c=True)
                   foo('', True, 42, c=True, b='')
                                      
                   foo('', b='', c=True<warning descr="Parameter 'args' unfilled, expected '*tuple[*Ts, int]'">)</warning>
                   foo('', <warning descr="Expected type '*tuple[int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[str]' instead">''</warning>, b='', c=True)
                   foo('', <warning descr="Expected type '*tuple[str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[str, list]' instead">''</warning>, <warning descr="Expected type '*tuple[str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[str, list]' instead">[False]</warning>, b='', c=True)
                   foo('', <warning descr="Expected type '*tuple[str, str, str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[str, str, str, float]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[str, str, str, float]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[str, str, str, float]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, str, int]' (matched generic type '*tuple[*Ts, int]'), got '*tuple[str, str, str, float]' instead">1.1</warning>, b='', c=True)
                   """);
  }

  // PY-53105
  public void testVariadicGenericStarArgsTupleAndUnpackedTuple() {
    doTestByText("""
                   from typing import Tuple, TypeVarTuple
                                      
                   Ts = TypeVarTuple('Ts')
                                      
                                      
                   def foo(a: Tuple[*Ts], *args: *Tuple[str, *Ts, int], b: str) -> None: ...
                                      
                                      
                   foo(('', 1), '', '', 1, 1, b='')
                   foo((1,1), '', 1, 1, 1, b='')
                   foo(('',), '', '', 1, b='')
                   foo((), '', 1, b='')
                   foo(([], {}), '', [], {}, 1, b='')
                                      
                   foo(('', 1), b=''<warning descr="Parameter 'args' unfilled, expected '*tuple[str, str, int, int]'">)</warning>
                   foo(('', 1), <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">1</warning>, b='')
                   foo((1,1), <warning descr="Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, <warning descr="Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, b='')
                   foo(('',), <warning descr="Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, <warning descr="Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, b='')
                   x: Any
                   foo((), <warning descr="Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, Any]' instead">''</warning>, <warning descr="Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, Any]' instead">42</warning>, <warning descr="Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, Any]' instead">x</warning>, b='')
                   foo(([], {}), <warning descr="Expected type '*tuple[str, list, dict, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, list, dict]' instead">''</warning>, <warning descr="Expected type '*tuple[str, list, dict, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, list, dict]' instead">[]</warning>, <warning descr="Expected type '*tuple[str, list, dict, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, list, dict]' instead">{}</warning>, b='')
                   """);
  }

  // PY-53105
  public void testVariadicGenericStarArgsOfVariadicGeneric() {
    doTestByText("""
                   from typing import Tuple, TypeVarTuple

                   Ts = TypeVarTuple('Ts')


                   def foo(*args: Tuple[*Ts]): ...


                   foo((0,), (1,))
                   foo((0,), <warning descr="Expected type 'tuple[int]' (matched generic type 'tuple[*Ts]'), got 'tuple[int, int]' instead">(1, 2)</warning>)
                   # *tuple[int | str] is inferred for *Ts
                   foo((0,), ('1',))
                   """);
  }

  // PY-53105
  public void testVariadicGenericStarArgsOfVariadicGenericPrefixSuffix() {
    doTestByText("""
                   from typing import Tuple, TypeVarTuple
                                      
                   Ts = TypeVarTuple('Ts')
                                      
                                      
                   def foo(a: Tuple[*Ts], *args: *Tuple[str, *Ts, int], b: str) -> None: ...
                                      
                                      
                   foo(('', 1), '', '', 1, 1, b='')
                   foo((1,1), '', 1, 1, 1, b='')
                   foo(('',), '', '', 1, b='')
                   foo((), '', 1, b='')
                   foo(([], {}), '', [], {}, 1, b='')
                                      
                   foo(('', 1), b=''<warning descr="Parameter 'args' unfilled, expected '*tuple[str, str, int, int]'">)</warning>
                   foo(('', 1), <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">1</warning>, b='')
                   foo((1,1), <warning descr="Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, <warning descr="Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, b='')
                   foo(('',), <warning descr="Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, <warning descr="Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, b='')
                   x: Any
                   foo((), <warning descr="Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, Any]' instead">''</warning>, <warning descr="Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, Any]' instead">42</warning>, <warning descr="Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, Any]' instead">x</warning>, b='')
                   foo(([], {}), <warning descr="Expected type '*tuple[str, list, dict, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, list, dict]' instead">''</warning>, <warning descr="Expected type '*tuple[str, list, dict, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, list, dict]' instead">[]</warning>, <warning descr="Expected type '*tuple[str, list, dict, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, list, dict]' instead">{}</warning>, b='')
                   """);
  }

  // PY-53105
  public void testVariadicGenericStarArgsPrefixSuffix() {
    doTestByText("""
                   from typing import Tuple, TypeVarTuple
                                      
                   Ts = TypeVarTuple('Ts')
                                      
                                      
                   def foo(a: Tuple[*Ts], *args: *Tuple[str, *Ts, int], b: str) -> None: ...
                                      
                                      
                   foo(('', 1), '', '', 1, 1, b='')
                   foo((1,1), '', 1, 1, 1, b='')
                   foo(('',), '', '', 1, b='')
                   foo((), '', 1, b='')
                   foo(([], {}), '', [], {}, 1, b='')
                                      
                   foo(('', 1), b=''<warning descr="Parameter 'args' unfilled, expected '*tuple[str, str, int, int]'">)</warning>
                   foo(('', 1), <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, str, str, int]' instead">1</warning>, b='')
                   foo((1,1), <warning descr="Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, <warning descr="Expected type '*tuple[str, int, int, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, b='')
                   foo(('',), <warning descr="Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">''</warning>, <warning descr="Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, <warning descr="Expected type '*tuple[str, str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, int]' instead">1</warning>, b='')
                   x: Any
                   foo((), <warning descr="Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, Any]' instead">''</warning>, <warning descr="Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, Any]' instead">42</warning>, <warning descr="Expected type '*tuple[str, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, int, Any]' instead">x</warning>, b='')
                   foo(([], {}), <warning descr="Expected type '*tuple[str, list, dict, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, list, dict]' instead">''</warning>, <warning descr="Expected type '*tuple[str, list, dict, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, list, dict]' instead">[]</warning>, <warning descr="Expected type '*tuple[str, list, dict, int]' (matched generic type '*tuple[str, *Ts, int]'), got '*tuple[str, list, dict]' instead">{}</warning>, b='')
                   """);
  }

  // PY-53105
  public void testVariadicGenericStarArgsUnboundTuple() {
    doTestByText("""
                   from typing import Tuple


                   def foo(*args: *Tuple[int, ...]) -> None: ...


                   foo()
                   foo(1)
                   foo(1, 2, 3)

                   foo(<warning descr="Expected type 'int', got 'str' instead">''</warning>)
                   foo(1, <warning descr="Expected type 'int', got 'str' instead">''</warning>)
                   """);
  }

  // PY-53105
  public void testVariadicGenericMatchWithHomogeneousGenericVariadic() {
    doTestByText("""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[*tuple[Any, ...]] = Array()
                    
                    def expect_variadic_array(x: Array[int, *Shape]) -> None:
                        print(x)
                    
                    expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testVariadicGenericMatchWithHomogeneousGenericVariadicAndOtherTypes() {
    doTestByText("""
                    from __future__ import annotations
                                       
                    from typing import TypeVarTuple
                    from typing import Generic
                    from typing import Any
                    
                    Shape = TypeVarTuple("Shape")
                    
                    class Array(Generic[*Shape]):
                        ...
                    
                    y: Array[*tuple[Any, ...], int, str] = Array()
                    
                    def expect_variadic_array(x: Array[int, *Shape]) -> None:
                        print(x)
                    
                    expect_variadic_array(y)
                    """);
  }

  // PY-53105
  public void testVariadicGenericCheckTypeAliasesMissingParameter() {
    doTestByText("""
                   from typing import TypeVarTuple
                   from typing import TypeVar
                   from typing import Generic
                   from typing import NewType
                   
                   Shape = TypeVarTuple("Shape")
                   Height = NewType("Height", int)
                   Width = NewType("Width", int)
                   DType = TypeVar("DType")
                   
                   
                   class Array(Generic[DType, *Shape]):
                       ...
                   
                   
                   Float32Array = Array[float, *Shape]
                   
                   
                   def takes_float_array_of_specific_shape(arr: Float32Array[Height, Width]): ...
                   
                   
                   y: Float32Array[Height] = Array()
                   takes_float_array_of_specific_shape(<warning descr="Expected type 'Array[float, Height, Width]', got 'Array[float, Height]' instead">y</warning>)
                    """);
  }

  // PY-53105
  public void testVariadicGenericCheckTypeAliasesRedundantParameter() {
    doTestByText("""
                   from typing import TypeVarTuple
                   from typing import TypeVar
                   from typing import Generic
                   from typing import NewType
                   
                   Shape = TypeVarTuple("Shape")
                   Height = NewType("Height", int)
                   Width = NewType("Width", int)
                   DType = TypeVar("DType")
                   
                   
                   class Array(Generic[DType, *Shape]):
                       ...
                   
                   
                   Float32Array = Array[float, *Shape]
                   
                   
                   def takes_float_array_of_specific_shape(arr: Float32Array[Height]): ...
                   
                   
                   y: Float32Array[Height, Width] = Array()
                   takes_float_array_of_specific_shape(<warning descr="Expected type 'Array[float, Height]', got 'Array[float, Height, Width]' instead">y</warning>)
                    """);
  }

  // PY-53105
  public void testVariadicGenericEmpty() {
    doTestByText("""
                   from typing import TypeVarTuple
                                      
                   Ts = TypeVarTuple("Ts")
                                      
                   IntTuple = tuple[int, *Ts]
                                      
                   c: IntTuple[()] = <warning descr="Expected type 'tuple[int]', got 'tuple[int, str]' instead">(1, "")</warning>
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

  public void testNonGenericProtocolDoesNotMatchWithGenericClass() {
    doTestByText("""
                   from typing import Generic, Protocol, TypeVar
                   
                   T = TypeVar('T')
                   
                   class IntGetter(Protocol):
                       def get(self) -> int:
                           pass
                   
                   class Box(Generic[T]):
                       def get(self) -> T:
                           pass
                   
                   def f(x: IntGetter):
                       pass
                   
                   box: Box[str] = ...
                   f(<warning descr="Expected type 'IntGetter', got 'Box[str]' instead">box</warning>)
                   """);
  }

  public void testGenericProtocolDoesNotMatchWithGenericClass() {
    doTestByText("""
                   from typing import Generic, Protocol, TypeVar
                   
                   T = TypeVar('T')
                   
                   class Getter(Protocol[T]):
                       def get(self) -> T:
                           pass
                   
                   class Box(Generic[T]):
                       def get(self) -> T:
                           pass
                   
                   def f(x: Getter[int]):
                       pass
                   
                   box: Box[str] = ...
                   f(<warning descr="Expected type 'Getter[int]', got 'Box[str]' instead">box</warning>)
                   """);
  }

  // PY-53612
  public void testLiteralStringInPlaceOfStr() {
    doTest();
  }

  // PY-53612
  public void testLiteralStringEqualsToStr() {
    doTestByText("""
                   from typing_extensions import LiteralString
                   s: str
                   literal_string: LiteralString = <warning descr="Expected type 'LiteralString', got 'str' instead">s</warning>
                   literal_string: LiteralString = "hello"
                   """);
  }

  // PY-53612
  public void testLiteralStringAddition() {
    doTestByText("""
                   from typing_extensions import LiteralString
                   def expect_literal_string(s: LiteralString) -> None: ...
                                                                    
                   expect_literal_string("foo" + "bar")
                   literal_string: LiteralString
                   expect_literal_string(literal_string + "bar")
                             
                   literal_string2: LiteralString
                   expect_literal_string(literal_string + literal_string2)
                             
                   plain_string: str
                   expect_literal_string(<warning descr="Expected type 'LiteralString', got 'str' instead">literal_string + plain_string</warning>)
                   expect_literal_string(<warning descr="Expected type 'LiteralString', got 'str' instead">plain_string + literal_string</warning>)
                   """);
  }

  // PY-53612
  public void testLiteralStringJoin() {
    doTestByText("""
                   from typing import List
                   from typing_extensions import LiteralString
                   def expect_literal_string(s: LiteralString) -> None: ...
                   expect_literal_string(",".join(["foo", "bar"]))
                   literal_string: LiteralString
                   expect_literal_string(literal_string.join(["foo", "bar"]))
                   literal_string2: LiteralString
                   expect_literal_string(literal_string.join([literal_string, literal_string2]))
                             
                   xs: List[LiteralString]
                   expect_literal_string(literal_string.join(xs))
                   plain_string: str
                   expect_literal_string(<warning descr="Expected type 'LiteralString', got 'str' instead">plain_string.join([literal_string, literal_string2])</warning>)
                   expect_literal_string(<warning descr="Expected type 'LiteralString', got 'str' instead">literal_string.join([plain_string, literal_string2])</warning>)
                   """);
  }

  // PY-38873
  public void testTypedDictWithListField() {
    doTestByText("""
                   from typing import TypedDict, List, LiteralString
                   Movie = TypedDict('Movie', {'address': List[str]}, total=False)
                   class Movie2(TypedDict, total=False):
                       address: List[str]
                   movie = Movie()
                   movie2 = Movie2()
                   s1: LiteralString = <warning descr="Expected type 'LiteralString', got 'str' instead">movie['address'][0]</warning>
                   s2: LiteralString = <warning descr="Expected type 'LiteralString', got 'str' instead">movie2['address'][0]</warning>
                   """);
  }

  // PY-53612
  public void testLiteralInPlaceOfLiteralString() {
    doTestByText("""
                   from typing import LiteralString, Literal
                   def literal_identity(s: LiteralString) -> LiteralString:
                       return s
                   hello: Literal["hello"] = "hello"
                   literal_identity(hello)
                   """);
  }

  // PY-53612
  public void testStrInPlaceOfLiteralStringWithFString() {
    doTestByText("""
                   from typing import LiteralString
                   def expect_literal_string(s: LiteralString) -> None: ...
                   plain_string: str
                   literal_string: LiteralString
                   expect_literal_string(f"hello {literal_string}")
                   expect_literal_string(<warning descr="Expected type 'LiteralString', got 'str' instead">f"hello {plain_string}"</warning>)
                   """);
  }

  // PY-53612
  public void testGenericSubstitutionWithLiteralString() {
    doTestByText("""
                   from typing import TypeVar, LiteralString
                   T = TypeVar('T')
                   def calc(a: T, b: T):
                       pass
                   plain_string: str
                   literal_string: LiteralString
                   calc('literal string', plain_string)
                   calc(literal_string, plain_string)
                   """);
  }

  // PY-61137
  public void testLiteralStringInConditionalStatementsAndExpressions() {
    doTestByText("""
                   from typing import LiteralString
                   def condition1():
                       pass
                   def return_literal_string() -> LiteralString:
                       return "foo" if condition1() else "bar"  # OK
                   def return_literal_str2(literal_string: LiteralString) -> LiteralString:
                       return "foo" if condition1() else literal_string  # OK
                   """);
  }

  // PY-61137
  public void testLiteralInConditionalStatementsAndExpressions() {
    doTestByText("""
                   from typing import Literal
                   def condition1():
                       pass
                   def return_literal_string() -> Literal["foo", "bar"]:
                       return "foo" if condition1() else "bar"  # OK
                   def return_literal_str2(literal_string: Literal["foo"]) -> Literal["foo"]:
                       return "foo" if condition1() else literal_string  # OK
                   """);
  }

  // PY-61137
  public void testLiteralStringDoesNotGetCapturedInsideGenerics() {
    doTestByText("""
                   import typing
                   T = typing.TypeVar('T')
                   class Box(typing.Generic[T]):
                       def __init__(self, x: T) -> None:
                           ...
                   def same_type(b1: Box[T], b2: Box[T]):
                       ...
                   b = Box('foo'.upper())
                   same_type(b, Box('FOO'))
                   """);
  }

  // PY-61137
  public void testTypeVarBoundToLiteralString() {
    doTestByText("""
                   from typing import TypeVar, LiteralString
                   TLiteral = TypeVar("TLiteral", bound=LiteralString)
                   def literal_identity(s: TLiteral) -> TLiteral:
                       return s
                   s: LiteralString
                   y2 = literal_identity(s)
                   """);
  }

  // PY-62476
  public void testTypeGuardReturnTypeTreatedAsBool() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> doTestByText("""
from typing import TypeGuard
def foo(param: str | int) -> TypeGuard[str]:
    return <warning descr="Expected type 'TypeGuard[str]', got 'str | int' instead">param</warning>
      """)
    );
  }

  // PY-16994
  public void testCallableArity() {
    doTest();
  }

  // PY-64124
  public void testExpectedPositionalOnlyParameterMatchedWithRegularParameter() {
    doTest();
  }

  // PY-64124
  public void testExpectedKeywordOnlyParameterMatchedWithRegularParameter() {
    doTest();
  }

  // PY-55044
  public void testTypedDictKwargsArgument() {
    doTestByText("""
                   from typing import TypedDict, Unpack
                   
                   class Movie(TypedDict):
                       name: str
                   
                   def foo(**x: Unpack[Movie]):
                       pass
                   
                   foo(<warning descr="Expected type 'str', got 'int' instead">name=1</warning>)
                   """);
  }

  // PY-70528
  public void testVersionDependentTypeVarTupleInitialization() {
    doTestByText("""
                  import sys
                  
                  if sys.version_info >= (3, 11):
                      from typing import TypeVarTuple
                  else:
                      from typing_extensions import TypeVarTuple
                  
                  PosArgsT = TypeVarTuple("PosArgsT")
                  """);
  }

  // PY-23067
  public void testFunctoolsWraps() {
    doTestByText("""
                   import functools

                   class MyClass:
                     def foo(self, i: int):
                         pass

                   class Route:
                       @functools.wraps(MyClass.foo)
                       def __init__(self):
                           pass

                   class Router:
                       @functools.wraps(wrapped=Route.__init__)
                       def route(self, s: str):
                           pass

                   router = Router()
                   router.route(-2)
                   router.route(<warning descr="Expected type 'int', got 'str' instead">""</warning>)
                   """);
  }

  // PY-76399
  public void testAssignedValueMatchesWithDunderSetSimpleCase() {
    doTestByText("""                   
                   class MyDescriptor:
                   
                       def __set__(self, obj, value: str) -> None:
                           ...
                   
                   class Test:
                       member: MyDescriptor
                   
                   t = Test()
                   t.member = "str"
                   t.member = <warning descr="Expected type 'str' (from '__set__'), got 'int' instead">123</warning>
                   t.member = <warning descr="Expected type 'str' (from '__set__'), got 'Type[list]' instead">list</warning>
                   """);
  }

  // PY-76399
  public void testAssignedValueMatchesWithGenericDunderSetSimpleCase() {
    doTestByText("""                   
                   class MyDescriptor[T]:
                   
                       def __set__(self, obj, value: T) -> None:
                           ...
                   
                   class Test:
                       member: MyDescriptor[str]
                   
                   t = Test()
                   t.member = "str"
                   t.member = <warning descr="Expected type 'str' (from '__set__'), got 'int' instead">123</warning>
                   t.member = <warning descr="Expected type 'str' (from '__set__'), got 'Type[list]' instead">list</warning>
                   """);
  }

  // PY-76399
  public void testAssignedValueMatchesWithDunderSetWithOverloads() {
    doTestByText("""
                   from typing import overload
                   
                   class MyDescriptor:
                   
                       @overload
                       def __set__(self, obj: "Test", value: str) -> None:
                           ...
                       @overload
                       def __set__(self, obj: "Prod", value: "LocalizedString") -> None:
                           ...
                       def __set__(self, obj, value) -> None:
                           ...
                   
                   class Test:
                       member: MyDescriptor
                   
                   class Prod:
                       member: MyDescriptor
                   
                   class LocalizedString:
                       def __init__(self, value: str):
                           ...
                   
                   t = Test()
                   t.member = "abc"
                   t.member = <warning descr="Expected type 'str' (from '__set__'), got 'int' instead">42</warning>
                   p = Prod()
                   p.member = <warning descr="Expected type 'LocalizedString' (from '__set__'), got 'str' instead">"abc"</warning>
                   p.member = <warning descr="Expected type 'LocalizedString' (from '__set__'), got 'int' instead">42</warning>
                   """);
  }

  // PY-76399
  public void testAssignedValueMatchesWithDunderSetWithLiteralValue() {
    doTestByText("""
                   from typing import Literal
                   
                   
                   class MyDescriptor:
                       def __set__(self, obj, value: Literal[42]) -> None:
                           ...
                   
                   class Test:
                       member: MyDescriptor
                   
                   t = Test()
                   t.member = 42
                   t.member = <warning descr="Expected type 'Literal[42]' (from '__set__'), got 'Literal[43]' instead">43</warning>
                   t.member = <warning descr="Expected type 'Literal[42]' (from '__set__'), got 'Literal[\\"42\\"]' instead">"42"</warning>
                   """);
  }

  // PY-76399
  public void testAssignedValueMatchesWithDunderSetOfAttributeUsedInConstructor() {
    doTestByText("""
                   class MyDescriptor:
                       def __set__(self, obj: object, value: str): ...
                   
                   
                   class Test:
                       member: MyDescriptor
                   
                       def __init__(self, member):
                           self.member = member
                   
                   
                   x = Test("foo")
                   x.member = <warning descr="Expected type 'str' (from '__set__'), got 'int' instead">42</warning>
                   """);
  }

  // PY-77539
  public void testMatchingCallableParameterLists() {
    doTestByText("""
                   class MyCallable[**P, R]:
                       def __call__(self, *args: P.args, **kwargs: P.kwargs):
                           ...
                   compatible: MyCallable[[int], object] = MyCallable[[object], str]()
                   incompatible1: MyCallable[[object], object] = <warning descr="Expected type 'MyCallable[[object], object]', got 'MyCallable[[int], str]' instead">MyCallable[[int], str]()</warning>
                   incompatible2: MyCallable[[int], str] = <warning descr="Expected type 'MyCallable[[int], str]', got 'MyCallable[[object], object]' instead">MyCallable[[object], object]()</warning>
                   """);    
  }

  // PY-77541
  public void testMatchingUnboundParamSpecWithAnotherParamSpecInCustomGeneric() {
    doTestByText("""
                   class MyCallable[**P, R]:
                       def __call__(self, *args: P.args, **kwargs: P.kwargs):
                           ...
                   
                   def f[**P, R](callback: MyCallable[P, R]) -> MyCallable[P, R]:
                       ...
                   
                   def g[**P2, R2](callback: MyCallable[P2, R2]) -> MyCallable[P2, R2]:
                       return f(callback)
                   """);
  }

  // PY-23067
  public void testFunctoolsWrapsMultiFile() {
    doMultiFileTest();
  }

  // PY-76059
  public void testDataclassInstanceProtocol() {
    doTestByText("""
                   from dataclasses import dataclass, asdict
                   
                   @dataclass
                   class MyDataClass:
                       name:str
                   
                   asdict(MyDataClass(name="Bob"))
                   asdict(<warning descr="Expected type 'DataclassInstance', got 'str' instead">"Bob"</warning>)
                   """);
  }

  // PY-79129
  public void testTupleIndexOutOfRange() {
    doTestByText("""
                   from typing import Literal
                   
                   def foo(t: tuple[int, str], i: Literal[1], j: Literal[3], k: Literal[-3]):
                       t[i]
                       t[-1]
                       t[<warning descr="Tuple index out of range">j</warning>]
                       t[<warning descr="Tuple index out of range">2</warning>]
                       t[<warning descr="Tuple index out of range">k</warning>]
                       t[<warning descr="Tuple index out of range">-4</warning>]
                   
                   def bar(t: tuple[int, ...]):
                       t[10]
                   """);
  }

  // PY-79163
  public void testLiteralTypeInferredForFinalVariableOrAttribute() {
    doTestByText("""
                   from typing import Literal, Final
                   
                   foo: Final = 3
                   def expects_three(x: Literal[3]) -> None: ...
                   
                   expects_three(foo)
                   
                   def bar():
                       var: Final = 3
                       expects_three(var)
                   """);
    doTestByText("""
                   from typing import Literal, Final
                   v: Final = [1, 2]
                   def expects_list(l: list[int]): ...
                   
                   expects_list(v)
                   """);
  }

  public void testExplicitlyParameterizedGenericConstructorCall() {
    doTestByText("""
                   class A[T]:
                       def __init__(self, v: T) -> None: ...

                   A[int](<warning descr="Expected type 'int' (matched generic type 'T'), got 'str' instead">""</warning>)
                   """);
  }

  public void testGenericInstanceAttribute() {
    doTestByText("""
                   from typing import Self
                   
                   class Node[T]:
                       x: T

                   Node[int].<warning descr="Access to generic instance variables via class is ambiguous">x</warning> = 1
                   Node[int].<warning descr="Access to generic instance variables via class is ambiguous">x</warning>
                   Node.<warning descr="Access to generic instance variables via class is ambiguous">x</warning> = 1
                   Node.<warning descr="Access to generic instance variables via class is ambiguous">x</warning>

                   p = Node[int]()
                   type(p).<warning descr="Access to generic instance variables via class is ambiguous">x</warning>
                   i: int = p.x
                   j: int = Node[int]().x
                   p.x = 1
                   
                   class A:
                       attr1: list[int]
                       attr2: list[Self]
                       attr3: Self
                   
                   A.attr1
                   A.attr2
                   A.attr3
                   """);
  }

  public void testGenericInstanceAttribute2() {
    doTestByText("""
                   class Node[T]:
                       m: map[str, list[T]]
                   
                   Node[int].<warning descr="Access to generic instance variables via class is ambiguous">m</warning> = {}
                   Node[int].<warning descr="Access to generic instance variables via class is ambiguous">m</warning>
                   Node.<warning descr="Access to generic instance variables via class is ambiguous">m</warning> # TODO = {}
                   Node.<warning descr="Access to generic instance variables via class is ambiguous">m</warning>
                   """);
  }
}
