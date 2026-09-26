// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.ui.ColorBlindness
import com.intellij.ide.ui.UISettings
import com.intellij.idea.TestFor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
@Subsystems.Inspections
@Layers.Functional
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
    // The union separator stays muted (the matched `int` member before it is now a navigable link, not highlighted).
    assertTrue("<span style=\"color: $mutedColor;\"> | </span>" in tooltip, tooltip)
    // The parameter names are never highlighted.
    assertNotHighlighted(tooltip, "a: ")
    assertNotHighlighted(tooltip, "b: ")
  }

  // A top-level union mismatch aligns members into columns (matching members pair up and stay plain, pipes line up),
  // and — because a provided value may legitimately omit some expected alternatives — flags ONLY a provided member the
  // expected union can't accept. So `range | int` assigned to `int | str` highlights just `range`; the absent `str` is
  // not highlighted, and its gap on the provided row is not painted.
  @Test
  fun `top-level union flags only the unassignable provided member`() {
    val tooltip = tooltipFor("""
      a: range | int
      b: int | str = a
    """)
    assertProvided(tooltip, "range")
    assertNotHighlighted(tooltip, "str")
    assertNotHighlighted(tooltip, "int")
    // The members are padded into shared columns, so the two rows are the same width and the pipes line up vertically.
    assertRowsAligned(tooltip)
  }

  // Members that share structure (here `list[int]` and `list[str]`) pair into one column and recurse, so only their
  // differing element is highlighted instead of the whole members landing in separate columns.
  @Test
  fun `top-level union pairs structurally-similar members`() {
    val tooltip = tooltipFor("""
      a: list[str] | bytes
      b: list[int] | bytes = a
    """)
    assertProvided(tooltip, "str")
    assertExpected(tooltip, "int")
    assertNotHighlighted(tooltip, "bytes")
    assertRowsAligned(tooltip)
  }

  // Non-mismatched type names keep their editor syntax-highlight colour — a builtin like `int` in the builtin colour,
  // `None` in the keyword colour — via the rich link+highlight rendering, instead of the plain code colour.
  @Test
  fun `matched type names keep their editor syntax colour`() {
    val tooltip = tooltipFor("""
      a: int | None | range
      b: int | None | str = a
    """)
    assertSyntaxColored(tooltip, "int")
    assertSyntaxColored(tooltip, "None")
  }

  // Two fully-disjoint unions (here `None` matches neither `int` nor `str`) have nothing to align, so the aligned diff
  // is suppressed — a grid of gaps is less useful than the plain message, which is shown instead.
  @Test
  fun `disjoint top-level unions show no diff`() {
    val tooltip = tooltipFor("""
      a: int | str = None
    """)
    assertTrue(codeLineTexts(tooltip).isEmpty(), "expected no aligned diff for fully-disjoint unions:\n$tooltip")
  }

  // The overload-call report (`No overload matches`) shares the grid, so via the single `typeValue` factory it gets the
  // SAME rich rendering as the structural diff: a matched type name keeps its syntax colour and is a navigable link.
  @Test
  fun `overload-call report syntax-colours and links its type names`() {
    val tooltip = tooltipFor("""
      from typing import overload
      @overload
      def f(a: int, x: None) -> int: ...
      @overload
      def f(a: str, x: None) -> str: ...
      f(None, None)
    """)
    assertSyntaxColored(tooltip, "None")   // the matched `None` keeps its keyword colour…
    // …and the EXPECTED candidate parameter types are navigable links too — `int`/`str` appear only in the candidate
    // rows, so these links prove the candidates' PyType reaches the grid (not just the row's red/green Side styling).
    assertTrue("href=\"#element/builtins.int\"" in tooltip, tooltip)
    assertTrue("href=\"#element/builtins.str\"" in tooltip, tooltip)
  }

  // A one-sided union member that isn't highlighted (a duplicate `(a: int) -> None`, which IS assignable to `C`) is
  // still recursed so its parts are syntax-coloured + linked, instead of rendering as one flat, uncoloured string.
  @Test
  fun `a non-highlighted one-sided union member is syntax-coloured`() {
    val tooltip = tooltipFor("""
      from typing import Protocol
      def f1(a: int): ...
      def f2(a: int): ...
      def f3(b: int): ...
      class C(Protocol):
          def __call__(self, a: int): ...
      x: C = f1 or f2 or f3
    """)
    // The paired member and the expected `C` each contribute an `int` link; the duplicate one-sided member now
    // contributes one too (before the fix it was flat text) — so there are at least three `int` links.
    val intLinks = Regex("#element/builtins\\.int").findAll(tooltip).count()
    assertTrue(intLinks >= 3, "the non-highlighted one-sided member's `int` should be a link too (found $intLinks):\n$tooltip")
  }

  // A generic overload-call report shows each candidate in SOURCE form: its `[T: int]` type-parameter list and the
  // parameters in terms of the type variable `T`, NOT the `int` solved for `T` at this call site.
  @Test
  fun `generic overload-call report shows type parameters and the declared type variable`() {
    val tooltip = tooltipFor("""
      from typing import overload
      @overload
      def f[T: int](t: T, t2: T) -> int: ...
      @overload
      def f[T: int](t: None, t2: None) -> int: ...
      f(1, "")
    """)
    val candidate = codeLineTexts(tooltip).first { "t2" in it }
    assertTrue("[T: int]" in candidate, "the candidate should show its type-parameter list: $candidate")
    assertTrue("t: T" in candidate && "t2: T" in candidate,
               "parameters should show the declared type variable `T`, not the solved `int`: $candidate")
  }

  // The overload-call report goes through the SAME grid as the structural diff, so it colours mismatches identically:
  // the unmatched provided argument is red and the expected candidate parameters it failed are green — each over a
  // background band — instead of a flat bare-red. (The report used to omit the per-row side and fall back to plain red.)
  @Test
  fun `overload-call report colours provided red and expected green over a background`() {
    val tooltip = tooltipFor("""
      from typing import overload
      @overload
      def f(a: int, x: None) -> int: ...
      @overload
      def f(a: str, x: None) -> str: ...
      f(None, None)
    """)
    assertProvided(tooltip, "None")   // the unmatched provided argument `None` is red…
    assertExpected(tooltip, "int")    // …and the expected candidate parameters it failed are green…
    assertExpected(tooltip, "str")
    // …each highlight over a background band, exactly like the two-row structural diff (not a flat bare red).
    assertTrue("color: $providedColor; background-color:" in tooltip, tooltip)
    assertTrue("color: $expectedColor; background-color:" in tooltip, tooltip)
  }

  // A provided `async def` shows its source `async` modifier (highlighted, since the expected callable is sync) and
  // its return type in source form (`int`), not the desugared `Coroutine[Any, Any, int]`.
  @Test
  fun `async callable shows the async modifier and the source return type`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      async def f(a: int) -> int: ...
      x: Callable[[int], int] = f
    """)
    assertDiffGrid(tooltip)
    val provided = codeLineTexts(tooltip)[1]
    assertTrue("async" in provided && provided.trimEnd().endsWith("-> int"),
               "provided row should read as source `async …-> int`: '$provided'")
    assertFalse("Coroutine" in provided, "the async return must be unwrapped to its source form: '$provided'")
    // The `async` modifier is the incompatibility, so it is highlighted on the provided (red) side.
    assertTrue(Regex("color: ${Regex.escape(providedColor)};[^\"]*\">async").containsMatchIn(tooltip), tooltip)
  }

  // An `async def` is compatible with an expected callable that returns an awaitable. Only the parameter is wrong, so
  // the diff must not flag the `async` modifier or the return type.
  @Test
  fun `async callable against an awaitable return flags only the real mismatch`() {
    val tooltip = tooltipFor("""
      from typing import Awaitable, Callable
      async def f(a: str) -> int: ...
      x: Callable[[int], Awaitable[int]] = f
    """)
    assertDiffGrid(tooltip)
    assertProvided(tooltip, "str")
    assertExpected(tooltip, "int")
    assertNotHighlighted(tooltip, "async")
    assertNotHighlighted(tooltip, "Awaitable")
    assertNotHighlighted(tooltip, "Coroutine")
  }

  // A provided generic function shows its `[T]` type-parameter list in source form (a bare `Callable[…]` has none).
  @Test
  fun `generic callable shows its type-parameter list in source form`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      async def f[T](t: T) -> int: ...
      x: Callable[[int], str] = f
    """)
    assertDiffGrid(tooltip)
    val provided = codeLineTexts(tooltip)[1]
    assertTrue("async" in provided && "[T]" in provided,
               "provided row should show the `async` modifier and `[T]` type-parameter list: '$provided'")
    assertFalse("Coroutine" in provided, "'$provided'")
  }

  // A method override whose only difference is a type-parameter BOUND (base `def f[T: str]`, override `def f[T: int]`)
  // renders a leading `[T: bound]` group so the differing bound is highlighted — the parameter lists alone are identical.
  @Test
  fun `override type-parameter bound mismatch is highlighted`() {
    val tooltip = diffTableTooltip<PyMethodOverridingInspection>("""
      class A:
          def f[T: str](self, t: T) -> None: ...
      class B(A):
          def f[T: int](self, t: T) -> None: ...
    """)
    assertNotNull(tooltip, "expected an override signature diff")
    val diff = tooltip!!
    assertProvided(diff, "int")   // the override's bound `int` is red…
    assertExpected(diff, "str")   // …and the base's bound `str` green
  }

  // An override that re-annotates the receiver (`self: int` vs `self: str`) keeps `self` in the diff and compares its
  // EXPLICIT annotation, so the difference is highlighted — an unannotated `self`'s implicit receiver type stays hidden.
  @Test
  fun `override with an explicit self annotation shows the difference`() {
    val tooltip = diffTableTooltip<PyMethodOverridingInspection>("""
      class A:
          def f(self: int): ...
      class B(A):
          def f(self: str): ...
    """)
    assertNotNull(tooltip, "expected an override diff")
    val diff = tooltip!!
    assertCodeLineContains(diff, "self")   // `self` is shown, not dropped…
    assertProvided(diff, "str")            // …its provided annotation `str` is red…
    assertExpected(diff, "int")            // …and its expected annotation `int` green
  }

  // When only the BASE annotates the receiver (`self: int`) and the override leaves it bare (`self`), the receiver is
  // still compared: the base's `int` is highlighted (the override's implicit `Self@B` doesn't satisfy it). The bare
  // `self` must line up under the base's `self` — not float under its `: ` — even though only one row shows a `: type`.
  @Test
  fun `override with a bare self against an explicit base annotation is highlighted and aligned`() {
    val tooltip = diffTableTooltip<PyMethodOverridingInspection>("""
      class A:
          def f(self: int): ...
      class B(A):
          def f(self): ...
    """)
    assertNotNull(tooltip, "expected an override diff")
    val diff = tooltip!!
    assertNotHighlighted(diff, "self")     // the name itself is never flagged…
    assertExpected(diff, "int")            // …the base's explicit `int` is highlighted (the override doesn't match)…
    assertRowsAligned(diff)                // …and the bare `self` lines up under the base's `self` (same start),
    assertTrue(codeLineTexts(diff).all { it.startsWith("(self") }, diff)   // not shoved right under its `: `
  }

  // An intersection type (here produced by narrowing `x: A` with `isinstance(x, B)`) is shown member by member too,
  // its parts joined by ` & `, instead of falling back to a plain message.
  @Test
  fun `intersection type is shown member by member`() {
    val tooltip = tooltipFor("""
      class A: ...
      class B: ...
      def f(x: A):
          if isinstance(x, B):
              y: int = x
    """)
    assertTrue(" &amp; " in tooltip, tooltip)
    assertCodeLineContains(tooltip, "A")
    assertCodeLineContains(tooltip, "B")
  }

  // The diff is wired into assert_type, which is an EXACT-match check, so it compares the types INVARIANTLY: a
  // difference a covariant comparison would accept is still flagged. Here the callable returns `bool` where `int` is
  // expected — `bool` is a subtype of `int`, so a covariant return would NOT be a mismatch — yet the diff flags it
  // (while the matching `int` parameters stay unhighlighted), which only happens under the invariant comparison.
  @Test
  fun `assert_type mismatch shows the diff invariantly`() {
    val tooltip = diffTableTooltip<PyAssertTypeInspection>("""
      from typing import assert_type, Callable
      def f() -> Callable[[int], bool]: ...
      assert_type(f(), Callable[[int], int])
    """)
    assertNotNull(tooltip, "expected a callable diff in the assert_type warning")
    val diff = tooltip!!
    assertProvided(diff, "bool")   // the provided `bool` return is red…
    assertExpected(diff, "int")    // …and the expected `int` return green — an exact mismatch a covariant return would accept
  }

  // The diff (with the breakdown below it) is wired into the Protocol member-type check.
  @Test
  fun `protocol member type mismatch shows the diff`() {
    val tooltip = diffTableTooltip<PyProtocolInspection>("""
      from typing import Protocol, Callable
      class P(Protocol):
          f: Callable[[int], int]
      class C(P):
          f: Callable[[str], int]
    """)
    assertNotNull(tooltip, "expected a callable diff in the protocol member warning")
  }

  // ...and into the TypeVar default-vs-bound check.
  @Test
  fun `typevar default not matching bound shows the diff`() {
    val tooltip = diffTableTooltip<PyTypeHintsInspection>("""
      from typing import TypeVar, Callable
      T = TypeVar("T", bound=Callable[[int], int], default=Callable[[str], int])
    """)
    assertNotNull(tooltip, "expected a callable diff in the TypeVar bound warning")
  }

  // ...and into the __init__/__new__ signature-compatibility check.
  @Test
  fun `incompatible init and new signatures show the diff`() {
    val tooltip = diffTableTooltip<PyInitNewSignatureInspection>("""
      class C:
          def __new__(cls, x: int): ...
          def __init__(self, x: str): ...
    """)
    assertNotNull(tooltip, "expected a parameter diff in the __init__/__new__ warning")
  }

  // With more than one complementary signature (here both `__new__` and `__init__` are overloaded) there is no single
  // signature to diff against — comparing to an arbitrary one would highlight irrelevant differences — so the
  // structural diff is suppressed and the plain message shown. The incompatibility is still reported.
  @TestFor(issues = ["PY-90555"])
  @Test
  fun `ambiguous overloaded init and new show no diff`() {
    val code = """
      from typing import overload
      class C:
          @overload
          def __new__(cls, x: int): ...
          @overload
          def __new__(cls, x: str): ...
          @overload
          def __init__(self, y: bytes): ...
          @overload
          def __init__(self, y: bytearray): ...
    """
    assertTrue(warnings<PyInitNewSignatureInspection>(code).isNotEmpty(),
               "expected an __init__/__new__ incompatibility warning")
    assertNull(diffTableTooltip<PyInitNewSignatureInspection>(code),
               "expected no structural diff when there is no single complementary signature")
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

  // Red+green is exactly the pair red-green colour-vision deficiency can't separate, so the diff honours the IDE's
  // colour-blindness setting and switches to a distinguishable orange/blue pair instead.
  @Test
  fun `red-green colors are replaced under a colour-blindness setting`() {
    val settings = UISettings.getInstance()
    val previous = settings.colorBlindness
    settings.colorBlindness = ColorBlindness.deuteranopia
    try {
      val tooltip = tooltipFor("""
        from typing import Callable
        def f(a: int) -> object: ...
        x: Callable[[int], int] = f
      """)
      // The provided `object` and expected `int` are still highlighted — just not in the red/green a red-green
      // CVD user can't tell apart.
      assertFalse("color: $providedColor;" in tooltip, tooltip)
      assertFalse("color: $expectedColor;" in tooltip, tooltip)
      assertCodeLineContains(tooltip, "object")
      assertCodeLineContains(tooltip, "int")
    }
    finally {
      settings.colorBlindness = previous
    }
  }

  // Each class name in the diff is a navigable `#element/…` link to its declaration, and the mismatch colour is kept
  // inside the link (the link wraps the coloured span, so navigation is added without changing the diff's colours).
  @Test
  fun `type names are navigable element links`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int) -> object: ...
      x: Callable[[int], int] = f
    """)
    assertTrue("href=\"#element/builtins.int\"" in tooltip, tooltip)
    assertTrue("href=\"#element/builtins.object\"" in tooltip, tooltip)
    assertProvided(tooltip, "object")
    assertExpected(tooltip, "int")
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

  // The mirror of a missing parameter: a SURPLUS provided parameter (the expected signature has none at that
  // position — `Callable[[], None]` vs the provided `(a: int)`) is the WHOLE incompatibility, so its NAME is
  // highlighted red alongside its type — not just the type — while the expected row shows a gap.
  @Test
  fun `surplus provided parameter highlights its name and type`() {
    val tooltip = tooltipFor("""
      from typing import Callable
      def f(a: int): ...
      x: Callable[[], None] = f
    """)
    assertProvided(tooltip, "a: ")   // the surplus parameter's NAME is red…
    assertProvided(tooltip, "int")   // …as well as its type
    // ...and the expected row paints a gap where it has no parameter.
    assertTrue(missingGap.containsMatchIn(tooltip), tooltip)
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
    val tooltip = diffTableTooltip<PyOverloadsInspection>("""
      from typing import overload
      class C:
          @overload
          def f(self, a: int) -> int: ...
          @overload
          def f(self, a: str) -> str: ...
          def f(self, a: bytes) -> object: ...
    """)
    assertNotNull(tooltip, "expected a callable diff in an overload-vs-implementation warning")
  }

  // ...and into overlapping overloads with an incompatible return type (the return is shown highlighted).
  @Test
  fun `overlapping overload return diff`() {
    val tooltip = diffTableTooltip<PyOverloadsInspection>("""
      from typing import overload, Any
      class Animal: ...
      class Dog(Animal): ...
      class C:
          @overload
          def feed(self, a: Dog) -> str: ...
          @overload
          def feed(self, a: Animal) -> int: ...
          def feed(self, a: Any) -> Any: ...
    """)
    assertNotNull(tooltip, "expected a callable diff in an overlapping-overload warning")
    assertExpected(tooltip!!, "int")
  }

  // ...and into a method-override signature mismatch. The implicit `self`/`cls` receiver IS shown (so the signature
  // isn't truncated to an empty `()`), but only as the bare name — its implicit type (`Self@A` vs `Self@B`) differs by
  // design, so it stays hidden and `self` is never flagged; only the real parameter mismatch is highlighted.
  @Test
  fun `method override diff drops an implicit self receiver`() {
    val tooltip = diffTableTooltip<PyMethodOverridingInspection>("""
      class A:
          def f(self, x: int): ...
      class B(A):
          def f(self, x: str): ...
    """)
    assertNotNull(tooltip, "expected a parameter diff in a method-override warning")
    // The real mismatch is shown (provided `str` red, expected `int` green)...
    assertProvided(tooltip!!, "str")
    assertExpected(tooltip, "int")
    // ...and the receiver is gone: PyMethodOverridingInspection compares the two signatures with `self`/`cls` dropped
    // unless the BASE declares an explicit receiver contract, so there is no implicit `Self@A` vs `Self@B` noise — and
    // a `@staticmethod` override (which has no receiver at all) still lines its real parameters up column by column.
    assertFalse(codeLineTexts(tooltip).any { "self" in it }, tooltip)
  }

  // A `@staticmethod` override has no receiver while the base has `self`; because the implicit receiver is dropped from
  // BOTH sides, the real parameters still align instead of `self` being compared against the override's first argument.
  @TestFor(issues = ["PY-90555"])
  @Test
  fun `staticmethod override aligns its parameters against the base without the receiver`() {
    val tooltip = diffTableTooltip<PyMethodOverridingInspection>("""
      class A:
          def f(self, x: int): ...
      class B(A):
          @staticmethod
          def f(x: str): ...
    """)
    assertNotNull(tooltip, "expected a parameter diff in a method-override warning")
    assertFalse(codeLineTexts(tooltip!!).any { "self" in it }, tooltip)
    // `x` is the only parameter on each row, so the mismatch is its type — not a shifted-by-one gap.
    assertProvided(tooltip, "str")
    assertExpected(tooltip, "int")
    assertRowsAligned(tooltip)
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
    val tooltip = warningTooltips<PyTypeCheckerInspection>("""
      from typing import overload
      @overload
      def f(a: bool) -> bool: ...
      @overload
      def f(a: str) -> str: ...
      def f(a): ...
      f(None)
    """).firstOrNull { "<code" in it }
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

    val callableDiff = warnings<PyTypeCheckerInspection>("""
      from typing import Callable
      def f(a: int) -> int: ...
      x: Callable[[int], str] = f
    """).firstNotNullOf { it.toolTip }
    assertFalse(callableDiff.let { "Provided:" in it || "Expected:" in it }, callableDiff)

    val overloadGrid = warningTooltips<PyTypeCheckerInspection>("""
      from typing import overload
      @overload
      def f(a: bool) -> bool: ...
      @overload
      def f(a: str) -> str: ...
      def f(a): ...
      f(None)
    """)
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
    val warnings = warnings<PyTypeCheckerInspection>("""
      from typing import Callable
      def f(a: object) -> int: ...
      x: Callable[[int], object] = f
    """)
    assertTrue(warnings.isEmpty(), "expected no warnings but got: ${warnings.map { it.description }}")
  }

  // The overload report shows each candidate's FULL parameter list, so a parameter the call omits because it is
  // OPTIONAL is still shown (unhighlighted — it isn't why the overload fails), instead of being dropped.
  @TestFor(issues = ["PY-90555"])
  @Test
  fun `overload report shows an omitted optional parameter`() {
    val tooltip = warningTooltips<PyTypeCheckerInspection>("""
      from typing import overload
      @overload
      def f(c: int, d: bool = ...) -> int: ...
      @overload
      def f(c: str) -> str: ...
      f(None)
    """).first { "<code" in it }
    // The optional `d` is shown on the first candidate row…
    assertCodeLineContains(tooltip, "d: bool")
    // …but not highlighted (it isn't the incompatibility — `None` failing `c` is)…
    assertNotHighlighted(tooltip, "d: ")
    assertNotHighlighted(tooltip, "bool")
    // …while the incompatible `c` types on both candidates are.
    assertExpected(tooltip, "int")
    assertExpected(tooltip, "str")
  }

  // An overloaded call where the only arity-complete overload has a type mismatch and the other is dropped for an
  // arity issue used to collapse to a single "expected …, got …" message; now the report lists every overload, and a
  // required parameter an overload leaves unfilled is shown as wholly missing (its name and type highlighted).
  @TestFor(issues = ["PY-90555"])
  @Test
  fun `overload report lists every overload and flags a missing required parameter`() {
    val tooltip = warningTooltips<PyTypeCheckerInspection>("""
      from typing import overload
      @overload
      def f(c: int, d: bool) -> int: ...
      @overload
      def f(c: str) -> str: ...
      f(1)
    """).first { "<code" in it }
    // Both overloads are listed…
    val rows = codeLineTexts(tooltip).filter { it.startsWith("(") }
    assertTrue(rows.any { "c: int" in it && "d: bool" in it }, tooltip)
    assertTrue(rows.any { it.trim() == "(c: str)" || "c: str" in it }, tooltip)
    // …the missing required `d` is highlighted whole — its NAME and its type…
    assertExpected(tooltip, "d: ")
    assertExpected(tooltip, "bool")
    // …the other overload's real type mismatch (`1` vs `str`) is highlighted…
    assertExpected(tooltip, "str")
    // …and the matching argument `1` is NOT flagged (it satisfies the first overload's `c: int`).
    assertNotHighlighted(tooltip, "1")
  }

  // A literal argument type is shown bare — `1`, not `Literal[1]` — in the overload report.
  @TestFor(issues = ["PY-90555"])
  @Test
  fun `overload report shows a literal argument bare`() {
    val tooltip = warningTooltips<PyTypeCheckerInspection>("""
      from typing import overload
      @overload
      def f(c: int, d: bool) -> int: ...
      @overload
      def f(c: str) -> str: ...
      f(1)
    """).first { "<code" in it }
    assertFalse("Literal" in tooltip, tooltip)
    // The argument row shows the bare literal value `1` (the name cell is right-aligned, so it is padded from the `(`).
    assertCodeLineContains(tooltip, "1")
  }

  // When a call leaves a required parameter unfilled on every overload (a pure arity error, reported by
  // PyArgumentListInspection), the missing parameter is highlighted whole — its NAME as well as its type.
  @TestFor(issues = ["PY-90555"])
  @Test
  fun `unfilled required parameter highlights its name and type`() {
    val tooltip = diffTableTooltip<PyArgumentListInspection>("""
      from typing import overload
      @overload
      def f(c: int, d: bool) -> int: ...
      @overload
      def f(c: str, d: str) -> str: ...
      f(None)
    """)
    assertNotNull(tooltip, "expected an overload grid for the unfilled parameter")
    // The missing `d` is highlighted whole on both candidate rows: its name `d: ` and its type.
    assertExpected(tooltip!!, "d: ")
    assertExpected(tooltip, "bool")
    assertExpected(tooltip, "str")
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

  /** Asserts [text] is wrapped in the platform's editor syntax-highlight colour span (its keyword/builtin colour,
   *  the name optionally a link, e.g. `<span style="color:#000080;"><a href="#element/builtins.int">int</a></span>`),
   *  not the diff's own red/green mismatch or muted colour. */
  private fun assertSyntaxColored(tooltip: String, text: String) {
    // Syntax-highlight spans use `color:#hex` (no space); the diff's own red/green/muted use `color: #hex` (a space),
    // so requiring no space skips a same-named mismatch (e.g. a red argument `None`) and finds the syntax-coloured one.
    val match = Regex("""<span style="color:(#[0-9a-fA-F]{6})[^"]*">(?:<a [^>]*>)?${Regex.escape(text)}</""").find(tooltip)
    assertNotNull(match, "expected a syntax-colour span around '$text' in:\n$tooltip")
    assertFalse(match!!.groupValues[1] in setOf(providedColor, expectedColor, mutedColor),
                "'$text' should keep its editor syntax colour, not a diff colour")
  }

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
    warnings<PyTypeCheckerInspection>(text).firstNotNullOf { it.toolTip }

  /** The first warning tooltip that carries an aligned callable diff (a `<code>` line), with [inspection] enabled. */
  private inline fun <reified InspectionClass : PyInspection> diffTableTooltip(@Language("python") text: String): String? =
    warningTooltips<InspectionClass>(text).firstOrNull { "<code" in it }

  private inline fun <reified InspectionClass : PyInspection> warningTooltips(@Language("python") text: String): List<String> =
    warnings<InspectionClass>(text).mapNotNull { it.toolTip }

  private inline fun <reified InspectionClass: PyInspection> warnings(@Language("python") text: String): List<HighlightInfo> {
    myFixture.configureByText(PythonFileType.INSTANCE, text.trimIndent())
    // The fixture is shared across the tests of this class, so enable the inspection only for this highlighting
    // pass and disable it again afterwards instead of accumulating it on the shared profile.
    val inspection = InspectionClass::class.java.getDeclaredConstructor().newInstance()
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
