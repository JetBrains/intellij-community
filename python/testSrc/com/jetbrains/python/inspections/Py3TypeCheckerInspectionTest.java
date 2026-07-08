// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.idea.TestFor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * legacy, use a `PyCodeInsightTestCase` suite
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
          
          Weak1(1, <warning descr="Expected type 'int', got 'Literal[\\"str\\"]' instead">"str"</warning>, <warning descr="Expected type 'list[_T]', got 'Literal[2]' instead">2</warning>)
          
          
          @attr.s
          class Weak2:
              x = attr.ib()
          
              @x.default
              def __init_x__(self):
                  return 1
          
          Weak2("str")
          
          
          @attr.s
          class Strong:
              x = attr.ib(type=int)
              y = attr.ib(default=0, type=int)
              z = attr.ib(default=attr.Factory(list), type=typing.List[int])
          
          Strong(1, <warning descr="Expected type 'int', got 'Literal[\\"str\\"]' instead">"str"</warning>, <warning descr="Expected type 'list[int]', got 'list[Literal[\\"str\\"]]' instead">["str"]</warning>)"""
      )
    );
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

  // PY-55691
  public void testAttrsDataclassProtocolMatchingDefine() {
    runWithAdditionalClassEntryInSdkRoots("packages", () ->
      doTestByText("""
                     import attrs
                     
                     @attrs.define
                     class User:
                         password: str
                     
                     attrs.fields(User)
                     """)
    );
  }

  // PY-55691
  public void testAttrsDataclassProtocolMatchingFrozen() {
    runWithAdditionalClassEntryInSdkRoots("packages", () ->
      doTestByText("""
                     import attrs
                     
                     @attrs.frozen
                     class User:
                         password: str
                     
                     attrs.fields(User)
                     """)
    );
  }

  // PY-80837
  public void testInitEnumMember() {
    doTestByText("""
                   from enum import Enum, IntEnum, StrEnum

                   class MyEnum(Enum):
                       A = 1
                       B = "string"
                       C = None

                   class MyIntEnum(IntEnum):
                       OK = 1
                       BAD = <warning descr="Expected type 'int', got 'Literal[\\"string\\"]' instead">"string"</warning>

                   class MyStrEnum(StrEnum):
                       OK = "a"
                       BAD = <warning descr="Expected type 'str', got 'Literal[1]' instead">1</warning>
                   """);
  }

  @TestFor(issues = "PY-90192")
  public void testInitEnumMemberCustomNew() {
    fixme("PY-90192", AssertionError.class, "Expected type 'int', got 'str' instead", () ->
    doTestByText("""
                   from enum import Enum

                   class A:
                       def __new__(cls, x: int, y: int):
                           return object.__new__(cls)

                   class MyEnum(A, Enum):
                       OK = 1, 2
                       BAD = 1, <warning descr="Expected type 'int', got 'str' instead">"abb"</warning>
                   """)
    );
  }

  @TestFor(issues = "PY-85704")
  public void testTypedDictAssignableToDictStrAny() {
    doTestByText(
      """
      from typing import TypedDict, Any, Mapping

      class TD(TypedDict):
          name: str
          data: int

      def accepts_json(data: dict[str, Any]): ...

      td: TD = {"name": "name", "data": 1}

      # `Any` as the value type opts out of value-type checking, so a TypedDict is assignable
      # to `dict[str, Any]` even though its keys are required.
      accepts_json(td)  # OK
      accepts_json(TD(name="name", data=1))  # OK
      json_dict: dict[str, Any] = td  # OK
      any_mapping: Mapping[str, Any] = td  # OK

      # A TypedDict is still not assignable to `dict[str, object]` or `dict[str, <concrete>]`.
      object_dict: dict[str, object] = <warning descr="Expected type 'dict[str, object]', got 'TD' instead">td</warning>
      str_dict: dict[str, int] = <warning descr="Expected type 'dict[str, int]', got 'TD' instead">td</warning>
      """
    );
  }

  // PY-59260
  public void testIntFlagValueType() {
    doTestByText("""
                   from enum import IntFlag, auto

                   # IntFlag should infer int
                   class IF(IntFlag):
                       FIRST = auto()
                       SECOND = auto()
                       THIRD = 42

                   # IntFlag.value should return int, so these should not produce type errors
                   variable: int = IF.FIRST.value
                   another_var: int = IF.SECOND.value
                   explicit_var: int = IF.THIRD.value

                   # This should produce a type error
                   wrong_var: str = <warning descr="Expected type 'str', got 'int' instead">IF.FIRST.value</warning>
                   """);
  }

  // PY-59260
  public void testEnumValueTypeInference() {
    doTestByText("""
                   from enum import Enum, IntFlag, StrEnum

                   # IntFlag should infer int
                   class IF(IntFlag):
                       A = 1
                   i: int = IF.A.value

                   # StrEnum should infer str
                   class SE(StrEnum):
                       B = "b"
                   s: str = SE.B.value

                   # str mixin should infer str
                   class StrMixin(str, Enum):
                       C = "c"
                   s2: str = StrMixin.C.value
                   s3: int = <warning descr="Expected type 'int', got 'str' instead">StrMixin.C.value</warning>

                   # Empty str mixin should also infer str
                   class EmptyStrMixin(str, Enum):
                       pass
                   def test_empty(x: EmptyStrMixin):
                       s4: str = x.value
                       i2: int = <warning descr="Expected type 'int', got 'str' instead">x.value</warning>
                   """);
  }

  // PY-59260
  public void testEmptyEnumValueTypes() {
    doTestByText("""
                   from enum import StrEnum, Enum

                   class EmptyStrEnum(StrEnum):
                       pass

                   class EmptyStrMixin(str, Enum):
                       pass

                   def test_empty_str_enum(x: EmptyStrEnum):
                       s: str = x.value
                       i: int = <warning descr="Expected type 'int', got 'str' instead">x.value</warning>

                   def test_empty_str_mixin(x: EmptyStrMixin):
                       s: str = x.value
                       i: int = <warning descr="Expected type 'int', got 'str' instead">x.value</warning>
                   """);
  }

  // PY-59260
  public void testEnumValueTypeIgnoresNonMembers() {
    doTestByText("""
                   from enum import Enum, nonmember

                   # Simple enum with just integer members
                   class SimpleEnum(Enum):
                       A = 1
                       B = 2

                   # Should infer int
                   x: int = SimpleEnum.A.value
                   y: str = <warning descr="Expected type 'str', got 'Literal[2]' instead">SimpleEnum.B.value</warning>

                   # Enum with non-member first, then actual members
                   class E(Enum):
                       # This should be classified as a non-member
                       HELPER_CONSTANT = nonmember("not a member")
                       # These are the actual members - should infer int from first member
                       FIRST_MEMBER = 42
                       SECOND_MEMBER = 43

                   # Should infer int from FIRST_MEMBER (ignoring HELPER_CONSTANT)
                   a: int = E.FIRST_MEMBER.value
                   b: str = <warning descr="Expected type 'str', got 'Literal[43]' instead">E.SECOND_MEMBER.value</warning>
                   """);
  }

  @TestFor(issues = "PY-12592")
  public void testUnpackAnnotatedTargetMismatch() {
    doTestByText(
      """
        a = 1, 2
        b: str
        b, c = <warning descr="Expected type 'str', got 'int' instead">a</warning>
        """);
  }

  public void testStarOperatorTypeMismatch() {
    doTestByText(
      """
        def f(a, b, c): pass

        f(*<warning descr="Expected an iterable, got 'Literal[1]'">1</warning>)
        f(*<warning descr="Expected an iterable, got 'Literal[1]'">(1)</warning>)
        (*<warning descr="Expected an iterable, got 'Literal[1]'">1</warning>,)
        [*<warning descr="Expected an iterable, got 'Literal[1]'">1</warning>]
        {*<warning descr="Expected an iterable, got 'Literal[1]'">1</warning>}
        {*<warning descr="Expected an iterable, got 'Literal[1]'">(1)</warning>}

        def g(**kwargs): pass

        g(**<warning descr="Expected a mapping, got 'Literal[1]'">1</warning>)
        g(**<warning descr="Expected a mapping, got 'Literal[1]'">(1)</warning>)
        {**<warning descr="Expected a mapping, got 'Literal[1]'">1</warning>}
        {**<warning descr="Expected a mapping, got 'Literal[1]'">(1)</warning>}
        """);
  }

  public void testStarOperatorTypeMismatchNoFalsePositive() {
    doTestByText(
      """
        def f(*args, **kwargs): pass

        f(*(1, 2, 3))
        f(**{"a": 1})
        """);
  }

  @TestFor(issues = "PY-12592")
  public void testStarUnpackInTypeContext() {
    doTestByText(
      """
        def f(*a: *tuple[int, str]): ...

        x: tuple[*tuple[int, str]]
        """);
  }

  @TestFor(issues = "PY-89352")
  public void testUnpackTargetCorrectType() {
    doTestByText(
      """
        x: int
        x, = <warning descr="Expected type 'int', got 'Literal[\\"a\\"]' instead">"a"</warning>,
        (x,) = <warning descr="Expected type 'int', got 'Literal[\\"a\\"]' instead">"a"</warning>,
        [x] = <warning descr="Expected type 'int', got 'Literal[\\"a\\"]' instead">"a"</warning>,
        """);
  }

  @TestFor(issues = "PY-89352")
  public void testUnpackTargetCorrectTypeStarred() {
    doTestByText(
      """
        x: int
        *x, = <warning descr="Expected type 'int', got 'list[int]' instead">[1, 2, 3]</warning>
        (*x,) = <warning descr="Expected type 'int', got 'list[int]' instead">[1, 2, 3]</warning>
        [*x] = <warning descr="Expected type 'int', got 'list[int]' instead">[1, 2, 3]</warning>
        """);
  }

  @TestFor(issues = "PY-12592")
  public void testSpread() {
    doTestByText(
      """
        a = []
        b, c, d = 1, *a

        t = (1, 2)
        b, c, d = 1, *t
        b, c, d, e = <warning descr="Not enough values to unpack from 'tuple[Literal[1], Literal[1], Literal[2]]': expected 4, got 3">1, *t</warning>
        """);
  }

  @TestFor(issues = {"PY-4357", "PY-4360", "PY-12592"})
  public void testTupleUnpackCountBalance() {
    doTestByText(
      """
        a, b, c = <warning descr="Too many values to unpack from 'tuple[Literal[1], Literal[2], Literal[3], Literal[4]]': expected 3, got 4">1, 2, 3, 4</warning>
        a, b, c, d = 1, 2, 3, 4
        a = 1, 2, 3, 4

        c = 1, 2, 3
        a, b = <warning descr="Too many values to unpack from 'tuple[Literal[1], Literal[2], Literal[3]]': expected 2, got 3">c</warning>
        (a, b) = <warning descr="Too many values to unpack from 'tuple[Literal[1], Literal[2], Literal[3]]': expected 2, got 3">1, 2, 3</warning>

        *a, b = 1, 2, 3
        *a, b, c = 1, 2
        b, c, *a, d = <warning descr="Not enough values to unpack from 'tuple[Literal[1], Literal[2]]': expected 3, got 2">1, 2</warning>
        <warning descr="Only one starred expression allowed in assignment">a, *b, c, *d</warning> = 1, 2, 3, 4, 5, 6
        """);
  }

  // PY-12592: a starred target must not shift the value bound to the targets that follow it
  @TestFor(issues = "PY-12592")
  public void testStarredTargetBindsCorrectValues() {
    doTestByText(
      """
        a: list[int | str]
        b: bool

        *a, b = 1, "a", False
        """);
  }

  @TestFor(issues = "PY-12592")
  public void testStarredTargetBindsLastValue() {
    doTestByText(
      """
        b: bool
        *a, b = 1, 2, <warning descr="Expected type 'bool', got 'Literal[\\"x\\"]' instead">"x"</warning>
        """);
  }

  @TestFor(issues = "PY-42473")
  public void testOverloadLiteralEnumImported() {
    runWithAdditionalFileInLibDir("m.py", """
      from enum import Enum, auto
      from typing import Literal, overload


      class E(Enum):
          a = auto()
          b = auto()


      @overload
      def f(x: Literal[E.a]) -> str: ...


      @overload
      def f(x: Literal[E.b]) -> int: ...


      def f(x: E) -> object: ...
      """, _ -> doTestByText(
      """
        from m import E, f

        a: int = f(E.b)
        b: str = f(E.a)
        """)
    );
  }

  @TestFor(issues = "PY-90219")
  public void testTupleNotCompatibleWithNamedTuple() {
    doTestByText(
      """
        from typing import NamedTuple

        class N(NamedTuple):
            a: int

        n1: N = N(1)
        n2: N = <warning descr="Expected type 'N', got 'tuple[Literal[1]]' instead">(1,)</warning>

        class M(NamedTuple):
            a: int

        n3: N = <warning descr="Expected type 'N', got 'M' instead">M(1)</warning>

        class N2(N): ...

        def f(n2: N2):
          n4: N = n2
        """);
  }

  // a granular suppression code silences only its own category of problems.
  @TestFor(issues = "PY-90265")
  public void testSuppressBadReturnSuppressesOnlyReturn() {
    doTestByText(
      """
        def g(x: int):
            pass

        def f() -> int:
            # noinspection bad-return
            return 'hello'

        g(<warning descr="Expected type 'int', got 'Literal[\\"a\\"]' instead">'a'</warning>)
        """);
  }

  // an unrelated code must not suppress a return-type mismatch.
  @TestFor(issues = "PY-90265")
  public void testSuppressBadArgumentTypeDoesNotSuppressReturn() {
    doTestByText(
      """
        def f() -> int:
            # noinspection bad-argument-type
            return <warning descr="Expected type 'int', got 'Literal[\\"hello\\"]' instead">'hello'</warning>
        """);
  }

  // the legacy `PyTypeChecker` umbrella code keeps suppressing every category.
  @TestFor(issues = "PY-90265")
  public void testSuppressPyTypeCheckerSuppressesEverything() {
    doTestByText(
      """
        def f() -> int:
            # noinspection PyTypeChecker
            return 'hello'
        """);
  }

  // a code placed on the enclosing function suppresses problems inside it.
  @TestFor(issues = "PY-90265")
  public void testSuppressBadReturnForFunctionScope() {
    doTestByText(
      """
        # noinspection bad-return
        def f() -> int:
            return 'hello'
        """);
  }

  // a comma-separated list of codes suppresses each listed category.
  @TestFor(issues = "PY-90265")
  public void testSuppressBadReturnAmongMultipleCodes() {
    doTestByText(
      """
        def f() -> int:
            # noinspection bad-return, bad-argument-type
            return 'hello'
        """);
  }

  // an incomplete augmented assignment (no right-hand side) must not throw an NPE.
  @TestFor(issues = "PY-90019")
  public void testIncompleteAugAssignmentDoesNotThrow() {
    doTestByText(
      """
        def f(n: int):
            n +=<EOLError descr="Expression expected"></EOLError>
        """);
  }
}
