// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.idea.TestFor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for the aligned structural "type diff" tooltip ([PyTypeDiff]) shown for callable, tuple and
 * generic type mismatches reported by [PyTypeCheckerInspection].
 *
 * asserts on the raw tooltip HTML — span colors, `<code>` row widths, muted vs. highlighted styling. Like an
 * editor diff, the incompatible parts of the provided value are red and those of the expected type are green.
 */
@TestFor(issues = ["PY-85381"])
class PyTypeDiffTest : PyCodeInsightTestCase() {

  private val providedColor = ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground())
  private val expectedColor = ColorUtil.toHtmlColor(UIUtil.getLabelSuccessForeground())
  private val mutedColor = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())

  /** A missing-position block: a background-only span over non-breaking padding (no text of its own), painted
   *  where one side lacks a parameter/type argument the other has. */
  private val missingGap = Regex("""<span style="[^"]*background-color:[^"]*">(?:&nbsp;)+</span>""")

  /** Per-test disposable; resets any registry override this test made when the test finishes (the fixture, and so
   *  the registry state, is shared across the class). */
  private lateinit var testDisposable: Disposable

  // The structural diff is off by default in production; these tests exercise it, so turn it on for each test.
  @BeforeEach
  fun enableDiffTooltips() {
    testDisposable = Disposer.newDisposable("PyTypeDiffTest diff-tooltip flag")
    Registry.get("python.type.checker.diff.tooltip").setValue(true, testDisposable)
  }

  @AfterEach
  fun resetDiffTooltips() {
    Disposer.dispose(testDisposable)
  }

  // The whole point: an incompatible parameter is highlighted, a compatible one is not.
  @Test
  fun `incompatible parameter is highlighted`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int, b: int) -> int: ...
      x: Callable[[int, int | str], int] = f
    """)
    // Both signatures are rendered as two aligned code lines, labeled so it's clear which is which.
    assertEquals(2, codeLineTexts(tooltip).size, tooltip)
    assertDiffGrid(tooltip)
    // In the expected union `int | str`, only the `str` member that the actual can't accept is highlighted (in
    // green, as the expected side) — the matching `int` member (followed by a muted ` | `) is not, and the whole
    // union is not one span.
    assertExpected(tooltip, "str")
    assertNotHighlighted(tooltip, "int | str")
    assertTrue("int<span style=\"color: $mutedColor;\"> | </span>" in tooltip, tooltip)
    // The parameter names are never highlighted.
    assertNotHighlighted(tooltip, "a: ")
    assertNotHighlighted(tooltip, "b: ")
  }

  // Like an editor diff, the provided value's incompatible parts are red and the expected type's are green, and
  // each highlight sits on a subtle background band.
  @Test
  fun `provided parts are red and expected parts green over a background`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int) -> object: ...
      x: Callable[[int], int] = f
    """)
    // The provided return `object` is red; the expected return `int` is green.
    assertProvided(tooltip, "object")
    assertExpected(tooltip, "int")
    // Both highlights carry a background-color band behind the colored text.
    assertTrue("color: $providedColor; background-color:" in tooltip, tooltip)
    assertTrue("color: $expectedColor; background-color:" in tooltip, tooltip)
  }

  // The name and the default value of an incompatible parameter are NOT highlighted — only its type is.
  @Test
  fun `only the parameter type is highlighted`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int, b: int = 1) -> int: ...
      x: Callable[[int, int | str], int] = f
    """)
    assertTrue("= ..." in tooltip, tooltip)
    // The type `int` of `b` is red (the provided side), but neither the name `b: ` nor the ` = ...` default is
    // (the default here is present on the actual side, which is allowed — only the narrower type is the problem).
    assertNotHighlighted(tooltip, "b: ")
    assertNotHighlighted(tooltip, " = ...")
    assertProvided(tooltip, "int")
  }

  // A dropped default value is an incompatibility on its own (even when the types match): the `= ...` is highlighted.
  @Test
  fun `missing default value is highlighted`() {
    val tooltip = tooltipFor("""
      from typing import Protocol
      def fn(a: int, b: int) -> int: ...
      class P(Protocol):
          def __call__(self, a: int, b: int = 1) -> int: ...
      xi: P = fn
    """)
    // The expected `= ...` is highlighted (green); the type `int` (which matches) is not.
    assertExpected(tooltip, " = ...")
  }

  // A mandatory parameter the provided callable lacks shows as a background-only "gap" block on the provided row
  // (a red band where the parameter should be), while the expected row shows the parameter it requires.
  @Test
  fun `missing mandatory parameter is shown as a gap`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int) -> int: ...
      x: Callable[[int, int], int] = f
    """)
    // a background-only span (no foreground `color:`) over non-breaking padding marks the missing position...
    assertTrue(missingGap.containsMatchIn(tooltip), tooltip)
    // ...and the expected row still shows the required second `int` highlighted (green).
    assertExpected(tooltip, "int")
  }

  // The counts match but the provided parameter is anonymous (a `Callable`) while the expected one is named and
  // keyword-callable (a protocol's `__call__`): the provided side can't accept the keyword the expected requires,
  // and has no name to color — so the missing mandatory name shows as a red background block, not an empty cell.
  @Test
  fun `missing parameter name is shown as a gap`() {
    val tooltip = tooltipFor("""
      from typing import Callable, Protocol
      class C(Protocol):
          def __call__(self, asdf: int) -> None: ...
      x: Callable[[int], None]
      c: C = x
    """)
    // the expected row shows the required name `asdf` (green)...
    assertExpected(tooltip, "asdf: ")
    // ...and the provided row paints a background block where that mandatory name is missing.
    assertTrue(missingGap.containsMatchIn(tooltip), tooltip)
  }

  // A default the expected callable has (making the parameter optional) but the provided one lacks is a mismatch
  // with no text on the provided side; the missing default shows as a red gap, not an invisible empty cell.
  @Test
  fun `missing default value is shown as a gap`() {
    val tooltip = tooltipFor("""
      from typing import Callable, Protocol
      class C(Protocol):
          def __call__(self, asdf: int = ..., /) -> None: ...
      x: Callable[[int], None]
      c: C = x
    """)
    // the expected row shows the optional ` = ...` (green)...
    assertExpected(tooltip, " = ...")
    // ...and the provided row paints a background block where that default is missing.
    assertTrue(missingGap.containsMatchIn(tooltip), tooltip)
  }

  // The tooltip opens with a mismatch headline.
  @Test
  fun `tooltip opens with a mismatch headline`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int) -> int: ...
      x: Callable[[int], str] = f
    """)
    assertTrue("does not match the expected type" in tooltip, tooltip)
  }

  // When a side is a named callable whose name the aligned rows don't show — a callable Protocol (its class name)
  // or the provided function (its name) — the headline names it so the reader knows which type each signature is.
  @Test
  fun `headline names a callable protocol and the provided function`() {
    val tooltip = tooltipFor("""
      from typing import Protocol
      def fn(a: int) -> int: ...
      class Comparator(Protocol):
          def __call__(self, a: str) -> int: ...
      c: Comparator = fn
    """)
    // The expected Protocol and the provided function are both named in the opening sentence, as `<code>` spans
    // (the header template's backticks are rendered as code, not shown literally).
    assertTrue("<code>Comparator</code>" in tooltip, tooltip)
    assertTrue("<code>fn</code>" in tooltip, tooltip)
    // The rows still show the structural `__call__` signature (the names add context, they don't replace it).
    assertDiffGrid(tooltip)
  }

  // A parameter-name mismatch (keyword-callable params must share a name) highlights the names.
  @Test
  fun `parameter name mismatch is highlighted`() {
    val tooltip = tooltipFor("""
      from typing import Protocol
      def fn(a: int, b: int = 1) -> int: ...
      class P(Protocol):
          def __call__(self, a: int, c: int = 1) -> int: ...
      xi: P = fn
    """)
    // The differing names `b` (provided) / `c` (expected) are highlighted; the matching `a` is not.
    assertProvided(tooltip, "b: ")
    assertExpected(tooltip, "c: ")
    assertNotHighlighted(tooltip, "a: ")
  }

  // A keyword-only parameter (after a bare `*`) can't accept a positional argument the expected callable sends,
  // so it's a mismatch because of how it's passed — the name is shown and highlighted even though the types agree.
  @Test
  fun `keyword-only parameter name mismatch is highlighted`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(*, a: int) -> int: ...
      x: Callable[[int], int] = f
    """)
    // The keyword-only marker is rendered, and the keyword-only parameter's name is shown and highlighted (red).
    assertCodeLineContains(tooltip, "(*, ")
    assertProvided(tooltip, "a: ")
    // The types themselves match, so neither `int` is highlighted — only the name/kind is the problem.
    assertNotHighlighted(tooltip, "int")
  }

  // A parameter type with shared structure (`list[int]` vs `list[str]`) is decomposed contravariantly so only
  // the differing element is highlighted — the `list[…]` wrapper is not.
  @Test
  fun `generic parameter type highlights only the differing argument`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: list[int]) -> int: ...
      x: Callable[[list[str]], int] = f
    """)
    assertProvided(tooltip, "int")
    assertExpected(tooltip, "str")
    assertNotHighlighted(tooltip, "list")
  }

  // When the container kinds themselves are incompatible (a `Sequence` is not a `list`), the base names are
  // highlighted too — not only the differing element — since the whole type, not just its argument, fails to match.
  @Test
  fun `incompatible container base is highlighted`() {
    val tooltip = tooltipFor("""
      from typing import Sequence
      x: Sequence[str]
      a: list[int] = x
    """)
    // Both the base names (Sequence/list) and the differing element (str/int) are highlighted, each on its side.
    assertProvided(tooltip, "Sequence")
    assertExpected(tooltip, "list")
    assertProvided(tooltip, "str")
    assertExpected(tooltip, "int")
  }

  // The reverse direction is a compatible container kind (a `list` IS a `Sequence`), so only the incompatible
  // element is highlighted — the base names are not.
  @Test
  fun `compatible container base is not highlighted`() {
    val tooltip = tooltipFor("""
      from typing import Sequence
      x: list[str]
      a: Sequence[int] = x
    """)
    assertProvided(tooltip, "str")
    assertNotHighlighted(tooltip, "list")
    assertNotHighlighted(tooltip, "Sequence")
  }

  // `list` is invariant, so its element must match exactly. The invariance propagates into the nested `Callable`,
  // whose parameter (`int` vs `bool`) is then the highlighted incompatibility — even though `Callable[[int], str]`
  // would be assignable to `Callable[[bool], str]` covariantly.
  @Test
  fun `invariant container of callables highlights the nested parameter`() {
    Registry.get("python.subtypechecks.respect.variance").setValue(true, testDisposable)
    val tooltip = tooltipFor("""
      from typing import Callable
      a: list[Callable[[int], str]]
      b: list[Callable[[bool], str]] = a
    """)
    // The differing callable parameter is highlighted on both sides; the `list`, the `str` return, and `Callable` are not.
    assertProvided(tooltip, "int")
    assertExpected(tooltip, "bool")
    assertNotHighlighted(tooltip, "str")
    assertNotHighlighted(tooltip, "list")
  }

  // A variadic generic (`TypeVarTuple`) whose instantiations differ in arity is aligned, with the surplus type
  // argument shown as an extra (highlighted) cell — instead of falling back to a flat "Expected A[int], got A[int, str]".
  @Test
  fun `TypeVarTuple surplus argument is highlighted`() {
    val tooltip = tooltipFor("""
      class A[*Ts]: ...
      a1: A[int, str]
      a2: A[int] = a1
    """)
    // The arguments are aligned in a grid (not the flat message), the surplus `str` is highlighted, the matching
    // `int` and the `A` wrapper are not.
    assertDiffGrid(tooltip)
    assertProvided(tooltip, "str")
    assertNotHighlighted(tooltip, "int")
    assertNotHighlighted(tooltip, "A")
  }

  // A generic with a fixed parameter before a `TypeVarTuple` aligns the fixed argument positionally and lets the
  // `TypeVarTuple` absorb the rest: the matching fixed `int` is not highlighted, the differing/surplus variadic ones are.
  @Test
  fun `TypeVarTuple with a fixed parameter aligns the rest`() {
    val tooltip = tooltipFor("""
      class A[T, *Ts]: ...
      a1: A[int, str, bytes]
      a2: A[int, bool] = a1
    """)
    assertNotHighlighted(tooltip, "int")
    assertProvided(tooltip, "str")
    assertExpected(tooltip, "bool")
    assertProvided(tooltip, "bytes")
  }

  // A `ParamSpec` generic shares the callable parameter-list rendering: its argument is shown with parens and
  // aligned parameter names (not flat square brackets), and a structured parameter type is decomposed so only the
  // differing sub-part is highlighted — `(a: list[int])` vs `(Sequence[str])` highlights just `int`/`str`.
  @Test
  fun `ParamSpec parameter list shares the callable rendering`() {
    val tooltip = tooltipFor("""
      from typing import Callable, Sequence
      class A[**P]:
          def __init__(self, f: Callable[P, int]) -> None: ...
      def f(a: list[int]) -> int: ...
      a3 = A(f)
      a4: A[Sequence[str]] = a3
    """)
    // The parameter list uses parens (callable rendering), not the flat `[a: ...]` square-bracket form.
    assertCodeLineContains(tooltip, "(a: list")
    assertFalse("[a: list" in tooltip, tooltip)
    // Only the differing element types are highlighted; the `list`/`Sequence` wrappers, the `A`, and the name aren't.
    assertProvided(tooltip, "int")
    assertExpected(tooltip, "str")
    assertNotHighlighted(tooltip, "list")
    assertNotHighlighted(tooltip, "Sequence")
  }

  // A `ParamSpec` generic (`A[**P]`) instantiated with a bare argument list of differing arity is aligned like a
  // variadic: the surplus argument (`str`) is the extra (highlighted) cell, the matching `int` and `A` wrapper are not.
  @Test
  fun `ParamSpec surplus argument is highlighted`() {
    val tooltip = tooltipFor("""
      class A[**P]: ...
      a3: A[int, str]
      a4: A[int] = a3
    """)
    assertDiffGrid(tooltip)
    assertProvided(tooltip, "str")
    assertNotHighlighted(tooltip, "int")
    assertNotHighlighted(tooltip, "A")
  }

  // Row labels (`Provided:`/`Expected:`) keep the normal foreground color, not the muted/grey delimiter color.
  @Test
  fun `row labels are not muted`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int) -> int: ...
      x: Callable[[int], str] = f
    """)
    // The label cells use a padding-only style (no color override), so they render in the normal color.
    assertTrue("padding: 0px 8px 0px 4px;\">Provided:</td>" in tooltip, tooltip)
    assertTrue("padding: 0px 8px 0px 4px;\">Expected:</td>" in tooltip, tooltip)
    assertNotMuted(tooltip, "Provided:")
  }

  // When the expected callable names no parameters (a bare `Callable[...]`), the actual names are hidden on the
  // parameters that line up, and shown only on the one that differs.
  @Test
  fun `anonymous expected callable hides matched parameter names`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: str, b: str) -> int: ...
      x: Callable[[str, int], int] = f
    """)
    val lines = codeLineTexts(tooltip)
    assertEquals(2, lines.size, tooltip)
    // The expected signature is on top, the provided one below. The matched first parameter shows just its type;
    // the mismatched second one keeps its `b: ` name on the provided row.
    assertEquals("(str,    int) -> int", lines[0].trim())
    assertEquals("(str, b: str) -> int", lines[1].trim())
    // `a:` is gone entirely; `b:` is present on the provided row (and rows stay aligned).
    assertFalse("a:" in lines[1], tooltip)
    assertTrue("b: str" in lines[1], tooltip)
    assertEquals(lines[0].length, lines[1].length, tooltip)
  }

  // A union return type (covariant) highlights only the member that isn't assignable to the expected return.
  @Test
  fun `union return type highlights only the bad member`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int) -> int | str: ...
      x: Callable[[int], int] = f
    """)
    // The actual return `int | str` — only `str` (not assignable to the expected `int`) is highlighted (red, the
    // provided side).
    assertProvided(tooltip, "str")
    assertNotHighlighted(tooltip, "int | str")
  }

  // Return type is covariant: a wider actual return than the expected one is flagged.
  @Test
  fun `incompatible return type is highlighted`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int) -> object: ...
      x: Callable[[int], int] = f
    """)
    assertProvided(tooltip, "object")
    assertNotHighlighted(tooltip, "a: ")
  }

  // Parameters with a default value are shown as `= ...`.
  @Test
  fun `default value is shown`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int, b: int = 0) -> int: ...
      x: Callable[[int, str], int] = f
    """)
    assertTrue("= ..." in tooltip, tooltip)
  }

  // `/` and `*args` are rendered instead of suppressing the diff entirely.
  @Test
  fun `slash and varargs are rendered`() {
    val slash = tooltipFor("""
      from typing import Callable
      def f(a: int, /, b: int) -> int: ...
      x: Callable[[int, str], int] = f
    """)
    assertTrue("<code" in slash, slash)
    assertTrue(", /," in slash, slash)

    val varargs = tooltipFor("""
      from typing import Callable
      def f(*args: int) -> int: ...
      x: Callable[[int], str] = f
    """)
    assertTrue("<code" in varargs, varargs)
    // The container marker is shown, but with the expected side anonymous its identifier is dropped: `*:`, not `*args`.
    assertCodeLineContains(varargs, "*: int")
    assertFalse("*args" in varargs, varargs)
  }

  // A `*args` on the actual side absorbs the remaining expected positional parameters: each is checked against
  // the container's element type, so a compatible trailing one is NOT flagged (the bug was reding it as "extra").
  @Test
  fun `varargs absorbs trailing parameters`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int, b: int, d: str, *args: tuple[int, ...]) -> int: ...
      x: Callable[[int, int | str, str, tuple[int, ...], tuple[int, ...]], int] = f
    """)
    // The container is shown once as `*:` (its `args` identifier dropped, expected side being anonymous).
    assertCodeLineContains(tooltip, "*: tuple[int, ...]")
    assertFalse("*args" in tooltip, tooltip)
    // The only real incompatibility is `b: int` vs `int | str` — its `str` member is highlighted (green, expected).
    assertExpected(tooltip, "str")
    // Neither `tuple[int, ...]` (both absorbed by `*args`, both compatible) is highlighted.
    assertNotHighlighted(tooltip, "tuple")
  }

  // A `*args` whose element type has structure is decomposed against the parameter it absorbs, so only the
  // differing sub-part is highlighted — `*: list[int]` vs `list[str]` highlights just `int`/`str`, not the whole `list[...]`.
  @Test
  fun `varargs element type is decomposed`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(*a: list[int]) -> int: ...
      x: Callable[[list[str]], int] = f
    """)
    assertCodeLineContains(tooltip, "*: list[int]")
    assertProvided(tooltip, "int")
    assertExpected(tooltip, "str")
    assertNotHighlighted(tooltip, "list")
  }

  // When a `*args` absorbs SEVERAL expected positionals, the expected side stays expanded so the reader sees each
  // parameter the spread is matched against; the container is shown once and reds when it rejects any of them.
  @Test
  fun `varargs absorbs many parameters`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(*args: int): ...
      x: Callable[[int, int, str, int, int], object] = f
    """)
    val lines = codeLineTexts(tooltip)
    // The expected side (top) keeps all five parameters; the provided side (bottom) shows the spread once.
    assertTrue("int, int, str, int, int" in lines[0], tooltip)
    assertTrue("*: int" in lines[1], tooltip)
    assertFalse("*args" in tooltip, tooltip)
    // `str` (which `*args: int` can't accept) is highlighted on the expected side, and the spread's own element
    // type reds on the provided side.
    assertExpected(tooltip, "str")
    assertProvided(tooltip, "int")
    assertRowsAligned(tooltip)
  }

  // A recursive type must not loop forever while decomposing it: the diff still renders (the depth guard caps the
  // structural descent). The PyCharm type system also bounds its own expansion, so this is mostly a safety net.
  @Test
  fun `recursive type does not loop forever`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      type A = list[B]
      type B = list[A]
      def f(a: A) -> int: ...
      x: Callable[[A], str] = f
    """)
    // The return type is the real mismatch and is still shown (the expected `str` highlighted in green).
    assertExpected(tooltip, "str")
  }

  // Building the diff for self-referential built-ins (`type` vs `object`) hits the type system's recursion guard;
  // the diff must degrade to the plain message instead of letting the recursion propagate. Driven through [test]
  // so the recursion-prevention assertion is active and would fail if the diff let the recursion escape.
  @Test
  @TestFor(issues = ["PY-20057"])
  fun `self-referential builtins do not recurse infinitely`() = test("""
    def expects_type(x: type): ...
    def expects_object(x: object):
        expects_type(x)  # WARNING Expected type 'type', got 'object' instead
  """)

  // The diff is also wired into the overload check: overload signature vs implementation.
  @Test
  fun `overload vs implementation diff`() {
    val tooltip = diffTableTooltip("""
      from typing import overload
      class C:
          @overload
          def f(self, a: int) -> int: ...
          @overload
          def f(self, a: str) -> str: ...
          def f(self, a: bytes) -> object: ...
    """, PyOverloadsInspection::class.java)
    assertNotNull(tooltip, "expected a callable diff in an overload-vs-implementation warning")
  }

  // ...and into overlapping overloads with an incompatible return type (the return is shown highlighted).
  @Test
  fun `overlapping overload return diff`() {
    val tooltip = diffTableTooltip("""
      from typing import overload, Any
      class Animal: ...
      class Dog(Animal): ...
      class C:
          @overload
          def feed(self, a: Dog) -> str: ...
          @overload
          def feed(self, a: Animal) -> int: ...
          def feed(self, a: Any) -> Any: ...
    """, PyOverloadsInspection::class.java)
    assertNotNull(tooltip, "expected a callable diff in an overlapping-overload warning")
    assertExpected(tooltip!!, "int")
  }

  // ...and into a method-override signature mismatch. The implicit `self`/`cls` receiver is not part of the
  // overridable signature — its type differs between base and override by design (`Self@A` vs `Self@B`) — so the
  // diff must drop it and show only the real parameter mismatch, never flagging `self`.
  @Test
  fun `method override diff drops self`() {
    val tooltip = diffTableTooltip("""
      class A:
          def f(self, x: int): ...
      class B(A):
          def f(self, x: str): ...
    """, PyMethodOverridingInspection::class.java)
    assertNotNull(tooltip, "expected a parameter diff in a method-override warning")
    // The real mismatch is shown (provided `str` red, expected `int` green)...
    assertProvided(tooltip!!, "str")
    assertExpected(tooltip, "int")
    // ...while `self` is dropped entirely, so it can't be (and isn't) highlighted as a mismatch.
    assertFalse(codeLineTexts(tooltip).any { "self" in it }, tooltip)
  }

  // Generalizes beyond callables: a nested generic/tuple mismatch aligns the type arguments and highlights the
  // incompatible one.
  @Test
  fun `generic and tuple type diff`() {
    val tooltip = tooltipFor("""
      from typing import Sequence
      a: tuple[bool, list[str], int]
      b: tuple[int, Sequence[object], str] = a
    """)
    assertTrue("tuple" in tooltip, tooltip)
    assertTrue("Sequence" in tooltip, tooltip)
    // The last element (int vs str) is the incompatibility; the list/Sequence element is compatible.
    assertExpected(tooltip, "str")
  }

  // The overload-call "no overload matches" report uses the same aligned, code-styled grid.
  @Test
  fun `overload call uses the code grid`() {
    val tooltip = warningTooltips("""
      from typing import overload
      @overload
      def f(a: bool) -> bool: ...
      @overload
      def f(a: str) -> str: ...
      def f(a): ...
      f(None)
    """, PyTypeCheckerInspection::class.java).firstOrNull { "<code" in it }
    assertNotNull(tooltip, "expected an aligned code grid in the no-overload-matches tooltip")
    assertTrue("Expected one of" in tooltip!!, tooltip)
    // The argument row and every candidate row are the same width, so the types line up under one another.
    // (Filter out the header's `<code>f</code>` span — only the `(...)` grid rows matter.)
    val rows = codeLineTexts(tooltip).filter { it.startsWith("(") }
    assertTrue(rows.size >= 2, tooltip)
    assertTrue(rows.all { it.length == rows[0].length }, "overload rows must be the same width:\n${rows.joinToString("\n")}")
  }

  // The structural diff is registry-gated; turning it off falls back to the plain message tooltip — neither the
  // two-row signature/type diff nor the overload-call grid is rendered.
  @Test
  fun `registry flag disables the diff tooltip`() {
    // Override the @BeforeEach-enabled flag on the same per-test disposable, which resets it after the test.
    Registry.get("python.type.checker.diff.tooltip").setValue(false, testDisposable)

    val callableDiff = warnings("""
      from typing import Callable
      def f(a: int) -> int: ...
      x: Callable[[int], str] = f
    """, PyTypeCheckerInspection::class.java).firstNotNullOf { it.toolTip }
    assertFalse(callableDiff.let { "Provided:" in it || "Expected:" in it }, callableDiff)

    val overloadGrid = warningTooltips("""
      from typing import overload
      @overload
      def f(a: bool) -> bool: ...
      @overload
      def f(a: str) -> str: ...
      def f(a): ...
      f(None)
    """, PyTypeCheckerInspection::class.java)
    assertTrue(overloadGrid.none { "Expected one of" in it && "<code" in it }, overloadGrid.toString())
  }

  // the two diff rows must have the same visible width — otherwise the columns don't line up.
  @Test
  fun `diff rows are aligned`() {
    assertRowsAligned(tooltipFor("""
      from typing import Callable
      def f(a: int, b: int = 1) -> int: ...
      x: Callable[[int, int | str], int] = f
    """))

    assertRowsAligned(tooltipFor("""
      from typing import Sequence
      a: tuple[bool, list[str], int]
      b: tuple[int, Sequence[object], str] = a
    """))
  }

  // A diff row must never wrap (wrapping would break the column alignment); a long signature gets a horizontal
  // scrollbar in the tooltip instead. Each `<code>` line therefore carries `white-space: nowrap`, overriding the
  // platform tooltip stylesheet's `code { overflow-wrap: anywhere; }`.
  @Test
  fun `diff rows do not wrap`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int, b: int = 1) -> int: ...
      x: Callable[[int, int | str], int] = f
    """)
    // The aligned diff rows carry `white-space: nowrap`; the breakdown `<code>` spans appended below the diff do not.
    val diffRows = Regex("<code[^>]*>").findAll(tooltip).map { it.value }.filter { "white-space: nowrap" in it }.toList()
    assertEquals(2, diffRows.size, tooltip)
  }

  // every type name gets the normal (value) color; structural names like `tuple` must not be
  // muted while `Sequence` is normal — that inconsistency was the bug.
  @Test
  fun `generic base names are not muted`() {
    val tooltip = tooltipFor("""
      from typing import Sequence
      a: tuple[bool, list[str], int]
      b: tuple[int, Sequence[object], str] = a
    """)
    assertNotMuted(tooltip, "tuple")
    assertNotMuted(tooltip, "list")
    assertNotMuted(tooltip, "Sequence")
  }

  // A compatible (widening) assignment must not produce any warning.
  @Test
  fun `compatible callable produces no warning`() {
    val warnings = warnings("""
      from typing import Callable
      def f(a: object) -> int: ...
      x: Callable[[int], object] = f
    """, PyTypeCheckerInspection::class.java)
    assertTrue(warnings.isEmpty(), "expected no warnings but got: ${warnings.map { it.description }}")
  }

  // ---- assertion helpers ------------------------------------------------------------------------------------

  /** Asserts [text] is rendered as a complete provided-side (red) highlight span — an incompatible part of the
   *  actual value. */
  private fun assertProvided(tooltip: String, text: String) =
    assertTrue(highlightSpan(providedColor, text).containsMatchIn(tooltip), tooltip)

  /** Asserts [text] is rendered as a complete expected-side (green) highlight span — an incompatible part of the
   *  expected type. */
  private fun assertExpected(tooltip: String, text: String) =
    assertTrue(highlightSpan(expectedColor, text).containsMatchIn(tooltip), tooltip)

  /** Asserts neither side highlights [text] (nor anything starting with it): no red or green span begins with it. */
  private fun assertNotHighlighted(tooltip: String, text: String) =
    assertFalse(highlightStart(text).containsMatchIn(tooltip), tooltip)

  /** Asserts no muted (delimiter-colored) span starts with [text]; type names must keep the normal color. */
  private fun assertNotMuted(tooltip: String, text: String) =
    assertFalse("color: $mutedColor;\">$text" in tooltip, tooltip)

  /** Asserts the tooltip is the two-row labeled diff grid (not the flat "Expected …, got …" message). */
  private fun assertDiffGrid(tooltip: String) =
    assertTrue("Provided:" in tooltip && "Expected:" in tooltip, tooltip)

  /** Asserts some rendered `<code>` line contains [text]. */
  private fun assertCodeLineContains(tooltip: String, text: String) =
    assertTrue(codeLineTexts(tooltip).any { text in it }, tooltip)

  private fun assertRowsAligned(tooltip: String) {
    val lines = codeLineTexts(tooltip)
    assertEquals(2, lines.size, tooltip)
    assertEquals(lines[0].length, lines[1].length, "the two rows must be the same visible width:\n${lines.joinToString("\n")}")
  }

  /** A complete highlight span whose content is exactly [text] in the given [color], tolerating the optional
   *  trailing `background-color` of a diff highlight. */
  private fun highlightSpan(color: String, text: String): Regex =
    Regex("color: ${Regex.escape(color)};[^\"]*\">${Regex.escape(text)}</span>")

  /** A red (provided) or green (expected) highlight span whose content starts with [text]. */
  private fun highlightStart(text: String): Regex =
    Regex("color: (?:${Regex.escape(providedColor)}|${Regex.escape(expectedColor)});[^\"]*\">${Regex.escape(text)}")

  // ---- fixture / parsing helpers ----------------------------------------------------------------------------

  /** The first warning tooltip (with [PyTypeCheckerInspection] enabled) that carries the aligned type diff. */
  private fun tooltipFor(@Language("python") text: String): String =
    warnings(text, PyTypeCheckerInspection::class.java).firstNotNullOf { it.toolTip }

  /** The first warning tooltip that carries an aligned callable diff (a `<code>` line), with [inspection] enabled. */
  private fun diffTableTooltip(@Language("python") text: String, inspection: Class<out PyInspection>): String? =
    warningTooltips(text, inspection).firstOrNull { "<code" in it }

  private fun warningTooltips(@Language("python") text: String, inspection: Class<out PyInspection>): List<String> =
    warnings(text, inspection).mapNotNull { it.toolTip }

  private fun warnings(@Language("python") text: String, inspectionClass: Class<out PyInspection>): List<HighlightInfo> {
    myFixture.configureByText(PythonFileType.INSTANCE, text.trimIndent())
    // The fixture is shared across the tests of this class, so enable the inspection only for this highlighting
    // pass and disable it again afterwards instead of accumulating it on the shared profile.
    val inspection = inspectionClass.getDeclaredConstructor().newInstance()
    myFixture.enableInspections(inspection)
    try {
      return myFixture.doHighlighting().filter { it.severity >= HighlightSeverity.WARNING && it.description != null }
    }
    finally {
      myFixture.disableInspections(inspection)
    }
  }

  /** The visible text of each aligned diff `<code>` row (each carries `white-space: nowrap`), tags stripped and
   *  entities decoded, for width comparisons. The breakdown `<code>` spans appended below the diff are ignored. */
  private fun codeLineTexts(tooltip: String): List<String> =
    Regex("""<code style="white-space: nowrap[^"]*">(.*?)</code>""").findAll(tooltip).map { match ->
      match.groupValues[1]
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ").replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&")
    }.toList()
}
