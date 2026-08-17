// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.psi.types.PyTypeChecker
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [PyTypeChecker.explainMismatch] — the structured breakdown shown when a type mismatch is reported.
 *
 * Most tests render the breakdown tree to text and assert on the ordered chain of reasons; the end-to-end
 * test additionally checks that the breakdown lands in the editor tooltip and never in the flat description.
 */
@TestFor(classes = [PyTypeChecker::class, PyTypeCheckerInspection::class], issues=["PY-80221"])
@Subsystems.Typing
@Layers.Functional
class PyTypeCheckerExplanationTest : PyCodeInsightTestCase() {

  @Test
  fun `protocol attribute mismatch nests the attribute and its type`() = test("""
    from typing import Protocol
    class A(Protocol):
        a: int
    class C:
        a: str
    x: A = C()  # WARNING TOOLTIP incompatible with protocol \n Attribute 'a' \n not assignable
    """.trimIndent())

  @Test
  fun `protocol missing member is reported`() = test("""
    from typing import Protocol
    class A(Protocol):
        a: int
    class C:
        b: int
    x: A = C()  # WARNING TOOLTIP incompatible with protocol \n Attribute 'a' is missing
    """.trimIndent())

  @Test
  fun `generic element mismatch is reported`() = test("""
    def get_ints() -> list[int]: ...
    x: list[str] = get_ints()  # WARNING TOOLTIP 'int' is not assignable to 'str' FIXME Type parameter 1 \n not assignable # PY-89564
    """.trimIndent())

  /**
   * `bool` is a subtype of `int`, so the failure is invariance, not assignability: the breakdown names the
   * offending type parameter and its owner instead of the backwards "int is not assignable to bool".
   */
  @Test
  fun `invariant generic rejects a subtype element`() = test("""
    actual: list[int] = [True] # WARNING FIXME TOOLTIP Type parameter '_T' of 'list' is invariant \n 'bool' \n 'int' # PY-89564
    """.trimIndent())

  @Test
  fun `nested generic mismatch keeps both levels`() = test("""
    def get_nested_strs() -> list[list[str]]: ...
    x: list[list[int]] = get_nested_strs()  # WARNING TOOLTIP 'str' is not assignable to 'int' FIXME Type parameter 1 \n Type parameter 1 \n not assignable # PY-89564
    """.trimIndent())

  @Test
  fun `custom generic class type argument mismatch is reported`() = test("""
    from typing import Generic, TypeVar
    T = TypeVar("T")
    class Box(Generic[T]):
        def get(self) -> T: ...
    def get_box() -> Box[str]: ...
    x: Box[int] = get_box()  # WARNING TOOLTIP 'str' is not assignable to 'int' FIXME Type parameter 1 \n not assignable # PY-89564
    """.trimIndent())

  @Test
  fun `heterogeneous tuple element mismatch is reported`() = test("""
    x: tuple[int, str] = (1, 2)  # WARNING TOOLTIP 'Literal[2]' is not assignable to 'str' FIXME Type parameter 2 \n not assignable # PY-89564
    """.trimIndent())

  /**
   * A NamedTuple is nominal (see PyTypeChecker.match(PyTupleType, PyTupleType)): a structurally matching
   * plain tuple still isn't an instance of it, so the failure is the whole-type mismatch, not a per-element one.
   */
  @Test
  fun `a plain tuple is not assignable to a NamedTuple`() = test("""
    from typing import NamedTuple
    class Point(NamedTuple):
        x: int
        y: int
    p: Point = ("a", "b")  # WARNING TOOLTIP not assignable to \n Point
    """.trimIndent())

  /**
   * A read-only property keeps the protocol member read-only, so the NamedTuple's frozen field matches
   * on writability, and the failure is the field type itself.
   */
  @Test
  fun `named tuple field type is checked against a protocol`() = test("""
    from typing import NamedTuple, Protocol
    class HasX(Protocol):
        @property
        def x(self) -> int: ...
    class P(NamedTuple):
        x: str
    def get_p() -> P: ...
    y: HasX = get_p()  # WARNING TOOLTIP incompatible with protocol \n Attribute 'x' \n not assignable
    """.trimIndent())

  @Test
  fun `union mismatch lists no matching member`() = test("""
    x: int | str = 1.5  # WARNING TOOLTIP not assignable to any member
    """)

  @Test
  fun `union mismatch breaks down the failure against each member`() = test("""
    from typing import Protocol
    class A(Protocol):
        a: int
    class B(Protocol):
        b: int
    class C:
        a: str
    x: A | B = C()  # WARNING TOOLTIP not assignable to any member \n incompatible with protocol 'A' \n Attribute 'a' \n not assignable \n incompatible with protocol 'B' \n Attribute 'b' is missing
    """.trimIndent())

  /** The actual side is the union: every member that isn't assignable to the expected type is listed. */
  @Test
  fun `a union value with a non-assignable member is reported per member`() = test("""
    def get_str_or_bytes() -> str | bytes: ...
    x: int = get_str_or_bytes()  # WARNING TOOLTIP Not all members of \n 'str' is not assignable \n 'bytes' is not assignable
    """.trimIndent())

  @Test
  fun `callable return type mismatch is reported`() = test("""
    from typing import Callable
    def f() -> str: ...
    x: Callable[[], int] = f  # WARNING TOOLTIP Return type is incompatible \n not assignable
    """.trimIndent())

  @Test
  fun `callable parameter type mismatch names the offending parameter`() = test("""
    from typing import Callable
    def f(x: str) -> None: ...
    y: Callable[[int], None] = f  # WARNING TOOLTIP Parameter 'x' has an incompatible type \n not assignable
    """.trimIndent())

  @Test
  fun `callable parameter type mismatch names the offending parameter among several`() = test("""
    from typing import Callable
    def fn(a: int, b: int) -> str: ...
    x: Callable[[int, int | str], str] = fn  # WARNING TOOLTIP Parameter 'b' has an incompatible type \n Not all members of 'int | str' are assignable to 'int' \n not assignable
    """.trimIndent())

  @Test
  fun `dropping a parameter default is reported with the parameter name`() = test("""
    class Z:
        def f(self, a: int = 1): ...
    class Y(Z):
        def f(self, a: int): ...  # WARNING TOOLTIP Parameter 'a' must have a default value
    """.trimIndent())

  @Test
  fun `a renamed parameter is reported with both names`() = test("""
    class Z:
        def f(self, a: int): ...
    class Y(Z):
        def f(self, b: int): ...  # WARNING TOOLTIP Expected a parameter named 'a', but found 'b'
    """.trimIndent())

  @Test
  fun `a missing parameter is reported by name`() = test("""
    class Z:
        def f(self, a: int, b: int): ...
    class Y(Z):
        def f(self, a: int): ...  # WARNING TOOLTIP Parameter 'b' is missing
    """.trimIndent())

  @Test
  fun `an extra required parameter is reported by name`() = test("""
    class Z:
        def f(self, a: int): ...
    class Y(Z):
        def f(self, a: int, b: int): ...  # WARNING TOOLTIP Unexpected required parameter 'b'
    """.trimIndent())

  @Test
  fun `method override dropping a default value names the parameter in its tooltip`() = test("""
    # The user-reported case: the override drops the base parameter's default value.
    class Z:
        def f(self, a: int = 1): ...
    class Y(Z):
        def f(self, a: int): ...  # WARNING TOOLTIP must have a default value
    """.trimIndent())

  @Test
  fun `overloaded callable that matches no signature is not assignable`() = test("""
    from typing import overload, Callable
    @overload
    def f(x: int) -> int: ...
    @overload
    def f(x: str) -> str: ...
    def f(x): ...
    y: Callable[[bytes], bytes] = f  # WARNING TOOLTIP not assignable
    """.trimIndent())

  @Test
  fun `typed dict field type mismatch names the offending key`() = test("""
    from typing import TypedDict
    class Movie(TypedDict):
        name: str
        year: int
    class Book(TypedDict):
        name: str
        year: str
    def get_book() -> Book: ...
    x: Movie = get_book()  # WARNING TOOLTIP Value of key 'year' has an incompatible type
    """.trimIndent())

  @Test
  fun `typed dict missing key is reported by name`() = test("""
    from typing import TypedDict
    class Movie(TypedDict):
        name: str
        year: int
    class Named(TypedDict):
        name: str
    def get_named() -> Named: ...
    x: Movie = get_named()  # WARNING TOOLTIP Key 'year' is missing
    """.trimIndent())

  /** No key to point at when the source isn't a TypedDict at all: keep the whole-type message. */
  @Test
  fun `typed dict value not a typed dict falls back to the coarse message`() = test("""
    from typing import TypedDict
    class Movie(TypedDict):
        name: str
        year: int
    x: Movie = 1  # WARNING TOOLTIP incompatible with TypedDict
    """.trimIndent())

  @Test
  fun `no breakdown when the types are assignable A`() = test("""
    actual: object = "s"
    """.trimIndent())

  @Test
  fun `no breakdown when the types are assignable B`() = test("""
    actual: int = True
    """.trimIndent())

  /**
   * End-to-end: the on-the-fly hover tooltip carries the nested breakdown. The protocol attribute name stays a
   * plain `<code>` span (not navigable), while the types in the breakdown are now clickable links resolved against
   * the reported element (PY-80221 reuses the PY-90264 convention). Asserting on the raw HTML fragments (`</`, `/>`)
   * keeps the tooltip markup instead of flattening it to plain text.
   */
  @Test
  fun `breakdown goes to the tooltip and not the description`() = test("""
    from typing import Protocol
    class A(Protocol):
        a: int
    class C:
        a: str
    expected: A = C()  # WARNING TOOLTIP incompatible with protocol \n not assignable \n <code>a</code> \n element/builtins.str \n element/builtins.int
    """.trimIndent())

  /**
   * Sibling of [breakdown goes to the tooltip and not the description]:
   * Asserts that the description does not contain: "incompatible with protocol", `<code>`, or backticks.
   */
  @Test
  fun `breakdown stays out of the flat description`() = test("""
    from typing import Protocol
    class A(Protocol):
        a: int
    class C:
        a: str
    expected: A = C()  # WARNING Expected type 'A', got 'C' instead
    """.trimIndent())

  @Test
  fun `method override return type mismatch carries the breakdown in its tooltip`() = test("""
    class Base:
        def f(self) -> int: ...
    class Derived(Base):
        def f(self) -> str: ...  # WARNING TOOLTIP not assignable
    """.trimIndent())

  @Test
  fun `method override parameter type mismatch carries the breakdown in its tooltip`() = test("""
    class Base:
        def f(self, x: int) -> None: ...
    class Derived(Base):
        def f(self, x: str) -> None: ...  # WARNING TOOLTIP not assignable
    """.trimIndent())

  @Test
  fun `yield type mismatch carries the breakdown in its tooltip`() = test("""
    from typing import Generator
    def g() -> Generator[int, None, None]:
        yield "s"  # WARNING TOOLTIP not assignable
    """.trimIndent())

  @Test
  fun `overload signature incompatible with implementation carries the breakdown in its tooltip`() = test("""
    from typing import overload
    class C:
        @overload
        def f(self, x: int) -> int: ...
        @overload
        def f(self, x: str) -> str: ...  # WARNING TOOLTIP not assignable
        def f(self, x: int): ...
    """.trimIndent())

  @Test
  // FIXME PY-91461
  @TestCaseOptions(enablePyAnyType = false)
  fun `generic argument mismatch carries the breakdown in its tooltip 2`() = test("""
        class Box[T]:
            def __init__(self, x: T) -> None:
                self.x = x
            def put(self, value: T) -> None:
                self.x = value
        c = Box(10)
        c.put("foo") # WARNING TOOLTIP not assignable
    """.trimIndent())

  /**
   * The substituted parameter type is `list[int]`; list is invariant and `bool` ≠ `int`, so the breakdown names the
   * type parameter and its owner rather than the backwards "int is not assignable to bool". The type-variable name
   * stays a plain `<code>` span while the owner class is a clickable link.
   */
  @Test
  fun `generic argument mismatch with an invariant type carries the breakdown in its tooltip`() = test("""
    class Box[T]:
        def __init__(self, x: T) -> None:
            self.x = x
        def put(self, value: T) -> None:
            self.x = value
    c = Box([10])
    data = [True]
    c.put(data)  # WARNING FIXME TOOLTIP invariant \n <code>_T</code> \n element/builtins.list # PY-89564
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-90532"])
  fun `unsupported operator breakdown shows inline reason for single receiver`() {
    // Only Single.__add__ can reject part of y, so there is a single problematic receiver member.
    val tooltip = tooltipOf(
      PyTypeCheckerInspection(),
      "is not supported between",
      """
    class A: ...
    class B: ...

    class Both:
        def __add__(self, other: A | B) -> "Both": ...

    class Single:
        def __add__(self, other: A) -> "Single": ...

    def f(x: Both | Single, y: A | B):
        _ = x + y
    """
    )

    assertContainsOrdered(
      tooltip,
      "is not supported between", "Single", "and", "B",
      "is not assignable to parameter", "other", "of", "Single.__add__", "with type", "A",
      "B", "is not assignable to", "A",
    )
    assertFalse("does not support" in tooltip,
                "Single receiver member must not get a grouping node: $tooltip")

    assertFalse("Both" in tooltip, "Non-rejecting union member must not appear: $tooltip")

    assertTrue("parameter <code>other</code>" in tooltip,
               "Must name __add__'s actual parameter ('other'), not the caller's argument name: $tooltip")
    assertTrue("href=\"#element/a.Single.__add__\"" in tooltip, tooltip)
  }

  @Test
  @TestFor(issues = ["PY-90532"])
  fun `unsupported operator breakdown groups by receiver when multiple members fail`() {
    val tooltip = tooltipOf(
      PyTypeCheckerInspection(),
      "is not supported between",
      """
    class Operand: ...
    class BadArg: ...

    class Left1:
        def __add__(self, other: Operand) -> "Left1": ...

    class Left2:
        def __add__(self, other: Operand) -> "Left2": ...

    def f(x: Left1 | Left2, y: BadArg):
        _ = x + y
    """
    )

    assertEquals(
      1,
      tooltip.occurrencesOf("is not supported between"),
      "Headline must not be duplicated per combination: $tooltip"
    )

    assertEquals(2, tooltip.occurrencesOf("does not support"), tooltip)
    assertEquals(2, tooltip.occurrencesOf("is not assignable to parameter"), tooltip)
    assertEquals(2, tooltip.occurrencesOf("parameter <code>other</code>"),
                 "Both groups must name __add__'s actual parameter ('other'): $tooltip")

    assertContainsOrdered(
      tooltip,
      "is not supported between", "Left1", "Left2", "and", "BadArg",
      "Member", "Left1", "of", "Left1", "Left2", "does not support", "with", "BadArg",
      "BadArg", "is not assignable to parameter", "other", "of", "Left1.__add__", "with type", "Operand",
      "BadArg", "is not assignable to", "Operand",
      "Member", "Left2", "of", "Left1", "Left2", "does not support", "with", "BadArg",
      "BadArg", "is not assignable to parameter", "other", "of", "Left2.__add__", "with type", "Operand",
      "BadArg", "is not assignable to", "Operand",
    )

    assertTrue("Left1.__add__" in tooltip, tooltip)
    assertTrue("Left2.__add__" in tooltip, tooltip)
  }

  @Test
  @TestFor(issues = ["PY-90532"])
  fun `unsupported operator breakdown names missing dunders when no overloads exist`() {
    val tooltip = tooltipOf(
      PyTypeCheckerInspection(),
      "is not supported between",
      """
    class Operand: ...

    class NoAdd: ...
    class HasAdd:
        def __add__(self, other: Operand) -> "HasAdd": ...

    def f(x: HasAdd | NoAdd, y: Operand):
        _ = x + y
    """
    )

    assertContainsOrdered(
      tooltip,
      "is not supported between", "NoAdd", "and", "Operand",
      "NoAdd", "does not define", "__add__", "and", "Operand", "does not define", "__radd__",
    )
    assertEquals(2, tooltip.occurrencesOf("does not define"), tooltip)

    assertFalse("HasAdd" in tooltip, "Non-rejecting union member must not appear: $tooltip")
  }

  @Test
  @TestFor(issues = ["PY-90532"])
  fun `unsupported operator between large unions is reported without a breakdown`() {
    val tooltip = tooltipOf(
      PyTypeCheckerInspection(),
      "is not supported between",
      """
    class Operand1: ...
    class Operand2: ...
    class Operand3: ...
    class Operand4: ...

    class L1:
        def __add__(self, other: Operand1 | Operand2 | Operand3 | Operand4) -> "L1": ...
    class L2:
        def __add__(self, other: Operand1 | Operand2 | Operand3 | Operand4) -> "L2": ...
    class L3:
        def __add__(self, other: Operand1 | Operand2 | Operand3 | Operand4) -> "L3": ...
    class L4:
        def __add__(self, other: Operand1 | Operand2 | Operand3 | Operand4) -> "L4": ...
    class NoAdd: ...

    def f(x: L1 | L2 | L3 | L4 | NoAdd, y: Operand1 | Operand2 | Operand3 | Operand4):
        _ = x + y
    """
    )

    assertContainsOrdered(
      tooltip,
      "is not supported between",
      "L1", "L2", "L3", "L4", "NoAdd", "and",
      "Operand1", "Operand2", "Operand3", "Operand4",
    )

    assertFalse("does not define" in tooltip, "Large unions must not get a breakdown: $tooltip")
    assertFalse("does not support" in tooltip, "Large unions must not get a breakdown: $tooltip")
    assertFalse("is not assignable to parameter" in tooltip, "Large unions must not get a breakdown: $tooltip")
    assertFalse("<br/>" in tooltip, "Large unions must stay a single-line message: $tooltip")
  }

  @Test
  @TestFor(issues = ["PY-90532"])
  fun `unsupported in breakdown does not misname the contained value as the receiver`() {
    val tooltip = tooltipOf(
      PyTypeCheckerInspection(),
      "is not supported between",
      """
    class Elem: ...
    class Bad1: ...
    class Bad2: ...

    class Box:
        def __contains__(self, item: Elem) -> bool: ...

    def f(x: Bad1 | Bad2, y: Box):
        _ = x in y
    """
    )

    assertContainsOrdered(
      tooltip,
      "is not supported between", "Bad1", "Bad2", "and", "Box",
      "Member", "Bad1", "of", "Bad1", "Bad2",
      "is not assignable to parameter", "item", "of", "Box.__contains__", "with type", "Elem",
      "Bad1", "is not assignable to", "Elem",
      "Member", "Bad2", "of", "Bad1", "Bad2",
      "is not assignable to parameter", "item", "of", "Box.__contains__", "with type", "Elem",
      "Bad2", "is not assignable to", "Elem",
    )
    assertFalse("does not support" in tooltip,
                "Single receiver (Box) must not get a grouping node: $tooltip")

    assertEquals(2, tooltip.occurrencesOf("parameter <code>item</code>"),
                 "Both members must name __contains__'s actual parameter ('item'), not the caller's 'x': $tooltip")
    assertTrue("Box.__contains__" in tooltip, tooltip)
  }

  @Test
  @TestFor(issues = ["PY-90532"])
  fun `unsupported in breakdown groups by the container union, not the contained value`() {
    val tooltip = tooltipOf(
      PyTypeCheckerInspection(),
      "is not supported between",
      """
    class Elem: ...
    class Bad: ...

    class Box1:
        def __contains__(self, item: Elem) -> bool: ...
    class Box2:
        def __contains__(self, item: Elem) -> bool: ...

    def f(x: Bad, y: Box1 | Box2):
        _ = x in y
    """
    )

    assertContainsOrdered(
      tooltip,
      "is not supported between", "Bad", "and", "Box1", "Box2",
      "Member", "Box1", "of", "Box1", "Box2", "does not support", "with", "Bad",
      "Bad", "is not assignable to parameter", "item", "of", "Box1.__contains__", "with type", "Elem",
      "Bad", "is not assignable to", "Elem",
      "Member", "Box2", "of", "Box1", "Box2", "does not support", "with", "Bad",
      "Bad", "is not assignable to parameter", "item", "of", "Box2.__contains__", "with type", "Elem",
      "Bad", "is not assignable to", "Elem",
    )

    assertEquals(2, tooltip.occurrencesOf("does not support"), tooltip)
    assertEquals(2, tooltip.occurrencesOf("parameter <code>item</code>"),
                 "Both groups must name __contains__'s actual parameter ('item'), not the caller's 'x': $tooltip")
    assertTrue("Box1.__contains__" in tooltip, tooltip)
    assertTrue("Box2.__contains__" in tooltip, tooltip)
  }

  private fun String.occurrencesOf(fragment: String): Int {
    var count = 0
    var from = indexOf(fragment)
    while (from >= 0) {
      count++
      from = indexOf(fragment, from + fragment.length)
    }
    return count
  }

  /** Enables [inspection], highlights [code] and returns the tooltip of the single problem whose description contains [descriptionMarker]. */
  private fun tooltipOf(inspection: com.intellij.codeInspection.LocalInspectionTool,
                        descriptionMarker: String,
                        @Language("Python") code: String): String {
    myFixture.enableInspections(inspection)
    try {
      myFixture.configureByText("a.py", code.trimIndent())
      val info: HighlightInfo = myFixture.doHighlighting().single { it.description?.contains(descriptionMarker) == true }
      assertFalse("not assignable" in info.description!!, "Description must stay flat: ${info.description}")
      val tooltip = info.toolTip
      assertNotNull(tooltip, "Expected a tooltip with the breakdown")
      return tooltip!!
    }
    finally {
      myFixture.disableInspections(inspection)
    }
  }

  private fun assertContainsOrdered(text: String, vararg fragments: String) {
    var from = 0
    for (fragment in fragments) {
      val index = text.indexOf(fragment, from)
      assertTrue(index >= 0, "Expected to find '$fragment' after index $from in:\n$text")
      from = index + fragment.length
    }
  }

  /**
   * Sibling of [generic argument mismatch with an invariant type carries the breakdown in its tooltip]:
   * Asserts that the warning description does not contain: "not assignable".
   */
  @Test
  fun `invariant generic breakdown stays out of the flat description`() = test("""
    class Box[T]:
        def __init__(self, x: T) -> None:
            self.x = x
        def put(self, value: T) -> None:
            self.x = value
    c = Box([10])
    data = [True]
    c.put(data)  # WARNING FIXME Expected type 'list[int]', got 'list[bool]' instead # PY-89564
    """.trimIndent())
}
