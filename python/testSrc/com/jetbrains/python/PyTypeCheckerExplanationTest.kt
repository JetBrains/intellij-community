// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.PyMethodOverridingInspection
import com.jetbrains.python.inspections.PyOverloadsInspection
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeMismatchExplanation
import com.jetbrains.python.psi.types.TypeEvalContext
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError
import kotlin.jvm.java

/**
 * Tests for [PyTypeChecker.explainMismatch] — the structured breakdown shown when a type mismatch is reported.
 *
 * Most tests render the breakdown tree to text and assert on the ordered chain of reasons; the end-to-end
 * test additionally checks that the breakdown lands in the editor tooltip and never in the flat description.
 */
@TestFor(classes = [PyTypeChecker::class, PyTypeCheckerInspection::class], issues=["PY-80221"])
class PyTypeCheckerExplanationTest : PyCodeInsightTestCase() {

  @Test
  fun `protocol attribute mismatch nests the attribute and its type`() {
    val text = renderExplanation("""
      from typing import Protocol
      class A(Protocol):
          a: int
      class C:
          a: str
      expected: A
      actual = C()
    """)
    assertContainsOrdered(text, "incompatible with protocol", "Attribute 'a'", "not assignable")
  }

  @Test
  fun `protocol missing member is reported`() {
    val text = renderExplanation("""
      from typing import Protocol
      class A(Protocol):
          a: int
      class C:
          b: int
      expected: A
      actual = C()
    """)
    assertContainsOrdered(text, "incompatible with protocol", "Attribute 'a' is missing")
  }

  @Test
  fun `generic element mismatch is reported`() {
    val text = renderExplanation("""
      expected: list[str]
      actual: list[int]
    """)
    PyTestCase.fixme("PY-89564", AssertionFailedError::class.java, "") {
      assertContainsOrdered(text, "Type parameter 1", "not assignable")
    }
  }

  @Test
  fun `invariant generic rejects a subtype element`() {
    // `bool` is a subtype of `int`, so the failure is invariance, not assignability: the breakdown names the
    // offending type parameter and its owner instead of the backwards "int is not assignable to bool".
    PyTestCase.fixme("PY-89564", AssertionFailedError::class.java, "") {
      val text = renderExplanation("""
      expected: list[int]
      actual = [True]
    """)
      assertContainsOrdered(text, "Type parameter '_T' of 'list' is invariant", "'bool'", "'int'")
    }
  }

  @Test
  fun `nested generic mismatch keeps both levels`() {
    val text = renderExplanation("""
      expected: list[list[int]]
      actual: list[list[str]]
    """)
    PyTestCase.fixme("PY-89564", AssertionFailedError::class.java, "") {
      assertContainsOrdered(text, "Type parameter 1", "Type parameter 1", "not assignable")
    }
  }

  @Test
  fun `custom generic class type argument mismatch is reported`() {
    val text = renderExplanation("""
      from typing import Generic, TypeVar
      T = TypeVar("T")
      class Box(Generic[T]):
          def get(self) -> T: ...
      expected: Box[int]
      actual: Box[str]
    """)
    PyTestCase.fixme("PY-89564", AssertionFailedError::class.java, "") {
      assertContainsOrdered(text, "Type parameter 1", "not assignable")
    }
  }

  @Test
  fun `heterogeneous tuple element mismatch is reported`() {
    val text = renderExplanation("""
      expected: tuple[int, str]
      actual = (1, 2)
    """)
    PyTestCase.fixme("PY-89564", AssertionFailedError::class.java, "") {
      assertContainsOrdered(text, "Type parameter 2", "not assignable")
    }
  }

  @Test
  fun `a plain tuple is not assignable to a NamedTuple`() {
    // A NamedTuple is nominal (see PyTypeChecker.match(PyTupleType, PyTupleType)): a structurally-matching
    // plain tuple still isn't an instance of it, so the failure is the whole-type mismatch, not a per-element one.
    val text = renderExplanation("""
      from typing import NamedTuple
      class Point(NamedTuple):
          x: int
          y: int
      expected: Point
      actual = ("a", "b")
    """)
    assertContainsOrdered(text, "not assignable to", "Point")
  }

  @Test
  fun `named tuple field type is checked against a protocol`() {
    // A read-only property keeps the protocol member read-only, so the NamedTuple's frozen field matches
    // on writability and the failure is the field type itself.
    val text = renderExplanation("""
      from typing import NamedTuple, Protocol
      class HasX(Protocol):
          @property
          def x(self) -> int: ...
      class P(NamedTuple):
          x: str
      expected: HasX
      actual: P
    """)
    assertContainsOrdered(text, "incompatible with protocol", "Attribute 'x'", "not assignable")
  }

  @Test
  fun `union mismatch lists no matching member`() {
    val text = renderExplanation("""
      expected: int | str
      actual = 1.5
    """)
    assertContainsOrdered(text, "not assignable to any member")
  }

  @Test
  fun `union mismatch breaks down the failure against each member`() {
    val text = renderExplanation("""
      from typing import Protocol
      class A(Protocol):
          a: int
      class B(Protocol):
          b: int
      class C:
          a: str
      expected: A | B
      actual = C()
    """)
    assertContainsOrdered(text, "not assignable to any member",
                          "incompatible with protocol 'A'", "Attribute 'a'", "not assignable",
                          "incompatible with protocol 'B'", "Attribute 'b' is missing")
  }

  @Test
  fun `a union value with a non-assignable member is reported per member`() {
    // Actual side is the union: every member that isn't assignable to the expected type is listed.
    val text = renderExplanation("""
      expected: int
      actual: str | bytes
    """)
    assertContainsOrdered(text, "Not all members of", "'str' is not assignable", "'bytes' is not assignable")
  }

  @Test
  fun `callable return type mismatch is reported`() {
    val text = renderExplanation("""
      from typing import Callable
      expected: Callable[[], int]
      def f() -> str: ...
      actual = f
    """)
    assertContainsOrdered(text, "Return type is incompatible", "not assignable")
  }

  @Test
  fun `callable parameter type mismatch names the offending parameter`() {
    val text = renderExplanation("""
      from typing import Callable
      expected: Callable[[int], None]
      def f(x: str) -> None: ...
      actual = f
    """)
    assertContainsOrdered(text, "Parameter 'x' has an incompatible type", "not assignable")
  }

  @Test
  fun `callable parameter type mismatch names the offending parameter among several`() {
    val text = renderExplanation("""
      from typing import Callable
      def fn(a: int, b: int) -> str: ...
      expected: Callable[[int, int | str], str]
      actual = fn
    """)
    assertContainsOrdered(text, "Parameter 'b' has an incompatible type",
                          "Not all members of 'int | str' are assignable to 'int'", "not assignable")
  }

  @Test
  fun `dropping a parameter default is reported with the parameter name`() {
    val text = renderExplanation("""
      def base(a: int = 1): ...
      def override(a: int): ...
      expected = base
      actual = override
    """)
    assertContainsOrdered(text, "Parameter 'a' must have a default value")
  }

  @Test
  fun `a renamed parameter is reported with both names`() {
    val text = renderExplanation("""
      def base(a: int): ...
      def override(b: int): ...
      expected = base
      actual = override
    """)
    assertContainsOrdered(text, "Expected a parameter named 'a', but found 'b'")
  }

  @Test
  fun `a missing parameter is reported by name`() {
    val text = renderExplanation("""
      def base(a: int, b: int): ...
      def override(a: int): ...
      expected = base
      actual = override
    """)
    assertContainsOrdered(text, "Parameter 'b' is missing")
  }

  @Test
  fun `an extra required parameter is reported by name`() {
    val text = renderExplanation("""
      def base(a: int): ...
      def override(a: int, b: int): ...
      expected = base
      actual = override
    """)
    assertContainsOrdered(text, "Unexpected required parameter 'b'")
  }

  @Test
  fun `method override dropping a default value names the parameter in its tooltip`() {
    // The user-reported case: the override drops the base parameter's default value.
    val tooltip = tooltipOf(PyMethodOverridingInspection(), "Signature of method", """
      class Z:
          def f(self, a: int = 1): ...
      class Y(Z):
          def f(self, a: int): ...
    """)
    // The tooltip is HTML, so the parameter name's quotes are entity-escaped; match the quote-free remainder.
    assertTrue("must have a default value" in tooltip, tooltip)
  }

  @Test
  fun `overloaded callable that matches no signature is not assignable`() {
    val text = renderExplanation("""
      from typing import overload, Callable
      @overload
      def f(x: int) -> int: ...
      @overload
      def f(x: str) -> str: ...
      def f(x): ...
      expected: Callable[[bytes], bytes]
      actual = f
    """)
    assertContainsOrdered(text, "not assignable")
  }

  @Test
  fun `typed dict field type mismatch names the offending key`() {
    val text = renderExplanation("""
      from typing import TypedDict
      class Movie(TypedDict):
          name: str
          year: int
      class Book(TypedDict):
          name: str
          year: str
      expected: Movie
      actual: Book
    """)
    assertContainsOrdered(text, "Value of key 'year' has an incompatible type")
  }

  @Test
  fun `typed dict missing key is reported by name`() {
    val text = renderExplanation("""
      from typing import TypedDict
      class Movie(TypedDict):
          name: str
          year: int
      class Named(TypedDict):
          name: str
      expected: Movie
      actual: Named
    """)
    assertContainsOrdered(text, "Key 'year' is missing")
  }

  @Test
  fun `typed dict value not a typed dict falls back to the coarse message`() {
    // No key to point at when the source isn't a TypedDict at all: keep the whole-type message.
    val text = renderExplanation("""
      from typing import TypedDict
      class Movie(TypedDict):
          name: str
          year: int
      expected: Movie
      actual = 1
    """)
    assertContainsOrdered(text, "incompatible with TypedDict")
  }

  @Test
  fun `no breakdown when the types are assignable`() {
    // The breakdown must be null exactly when the types actually match.
    assertNull(explain("""
      expected: object
      actual = "s"
    """))
    assertNull(explain("""
      expected: int
      actual = True
    """))
  }

  @Test
  fun `breakdown goes to the tooltip and not the description`() {
    // End-to-end: the highlight description stays flat (batch/golden tests rely on this),
    // while the on-the-fly hover tooltip carries the nested breakdown.
    val inspection = PyTypeCheckerInspection()
    myFixture.enableInspections(inspection)
    try {
      myFixture.configureByText("a.py", """
        from typing import Protocol
        class A(Protocol):
            a: int
        class C:
            a: str
        expected: A = C()
      """.trimIndent())
      val info: HighlightInfo = myFixture.doHighlighting().single { it.description?.contains("instead") == true }
      val description = info.description!!
      assertFalse("incompatible with protocol" in description, "Description must stay flat: $description")
      // The description stays plain text: no <code> blocks and no backtick markup leaks into it.
      assertFalse("<code>" in description, "Description must stay plain: $description")
      assertFalse("`" in description, "Description must stay plain: $description")
      val tooltip = info.toolTip
      assertNotNull(tooltip, "Expected a tooltip with the breakdown")
      assertTrue("incompatible with protocol" in tooltip!!, tooltip)
      assertTrue("not assignable" in tooltip, tooltip)
      // The protocol attribute name stays a plain <code> span (not navigable), while the types in the breakdown
      // are now clickable links resolved against the reported element (PY-80221 reuses the PY-90264 convention).
      assertTrue("<code>a</code>" in tooltip, tooltip)
      assertTrue("href=\"#element/builtins.str\"" in tooltip, tooltip)
      assertTrue("href=\"#element/builtins.int\"" in tooltip, tooltip)
    }
    finally {
      myFixture.disableInspections(inspection)
    }
  }

  @Test
  fun `method override return type mismatch carries the breakdown in its tooltip`() {
    val tooltip = tooltipOf(PyMethodOverridingInspection(), "Return type of method", """
      class Base:
          def f(self) -> int: ...
      class Derived(Base):
          def f(self) -> str: ...
    """)
    assertTrue("not assignable" in tooltip, tooltip)
  }

  @Test
  fun `method override parameter type mismatch carries the breakdown in its tooltip`() {
    val tooltip = tooltipOf(PyMethodOverridingInspection(), "Signature of method", """
      class Base:
          def f(self, x: int) -> None: ...
      class Derived(Base):
          def f(self, x: str) -> None: ...
    """)
    assertTrue("not assignable" in tooltip, tooltip)
  }

  @Test
  fun `yield type mismatch carries the breakdown in its tooltip`() {
    val tooltip = tooltipOf(PyTypeCheckerInspection(), "Expected yield type", """
      from typing import Generator
      def g() -> Generator[int, None, None]:
          yield "s"
    """)
    assertTrue("not assignable" in tooltip, tooltip)
  }

  @Test
  fun `overload signature incompatible with implementation carries the breakdown in its tooltip`() {
    val tooltip = tooltipOf(PyOverloadsInspection(), "not compatible with the implementation", """
      from typing import overload
      class C:
          @overload
          def f(self, x: int) -> int: ...
          @overload
          def f(self, x: str) -> str: ...
          def f(self, x: int): ...
    """)
    assertTrue("not assignable" in tooltip, tooltip)
  }

  @Test
  fun `generic argument mismatch carries the breakdown in its tooltip`() {
    val tooltip = tooltipOf(PyTypeCheckerInspection(), "Expected type 'int'", """
      class Box[T]:
          def __init__(self, x: T) -> None:
              self.x = x
          def put(self, value: T) -> None:
              self.x = value
      c = Box(10)
      c.put("foo")
    """)
    assertTrue("not assignable" in tooltip, tooltip)
  }

  @Test
  fun `generic argument mismatch with an invariant type carries the breakdown in its tooltip`() {
    PyTestCase.fixme("PY-89564", NoSuchElementException::class.java, "") {
      val tooltip = tooltipOf(PyTypeCheckerInspection(), "Expected type 'list[int]'", """
      class Box[T]:
          def __init__(self, x: T) -> None:
              self.x = x
          def put(self, value: T) -> None:
              self.x = value
      c = Box([10])
      data = [True]
      c.put(data)
    """)
      // The substituted parameter type is `list[int]`; list is invariant and `bool` ≠ `int`, so the breakdown
      // names the type parameter and its owner rather than the backwards "int is not assignable to bool".
      // The type-variable name stays a plain <code> span; the owner class is a clickable link.
      assertTrue("invariant" in tooltip, tooltip)
      assertTrue("<code>_T</code>" in tooltip, tooltip)
      assertTrue("href=\"#element/builtins.list\"" in tooltip, tooltip)
    }
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

  private fun explain(@Language("Python") code: String): PyTypeMismatchExplanation? {
    myFixture.configureByText("a.py", code.trimIndent())
    return runReadActionBlocking {
      val expectedElement = myFixture.findElementByText("expected", PyTargetExpression::class.java)
      val actualElement = myFixture.findElementByText("actual", PyTargetExpression::class.java)
      val context = TypeEvalContext.codeAnalysis(myFixture.project, myFixture.file)
      PyTypeChecker.explainMismatch(context.getType(expectedElement), context.getType(actualElement), context, actualElement)
    }
  }

  private fun renderExplanation(@Language("Python") code: String): String {
    val explanation = explain(code)
    assertNotNull(explanation, "Expected a breakdown but the types matched")
    return render(explanation!!, 0)
  }

  private fun render(node: PyTypeMismatchExplanation, depth: Int): String {
    val sb = StringBuilder()
    repeat(depth) { sb.append("  ") }
    // Assert against the Problems-view description form, where code-like spans are single-quoted (the rich,
    // clickable type/class links live in the separate tooltip form, exercised by PyInspectionTooltipLinkTest).
    sb.append(node.message.description).append('\n')
    for (child in node.children) {
      sb.append(render(child, depth + 1))
    }
    return sb.toString()
  }

  private fun assertContainsOrdered(text: String, vararg fragments: String) {
    var from = 0
    for (fragment in fragments) {
      val index = text.indexOf(fragment, from)
      assertTrue(index >= 0, "Expected to find '$fragment' after index $from in:\n$text")
      from = index + fragment.length
    }
  }
}
