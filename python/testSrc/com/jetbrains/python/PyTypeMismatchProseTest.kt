// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam
import com.jetbrains.python.inspections.PyInspectionMessages.ProblemMessage
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.PyTypeMismatchProse
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.PyMismatchStep
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeMismatchExplanation
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.TypedDictKeyProblem
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for [PyTypeMismatchProse] — the prose rendering of the [PyTypeChecker.explainMismatch] breakdown tree.
 *
 * The renderer is exercised two ways:
 *  - **unit** tests build a [PyTypeMismatchExplanation] tree by hand and assert on the flattened prose, so every
 *    [PyMismatchStep] kind (including the variance ones, whose *detection* is still gapped by PY-89564) is covered
 *    deterministically, independent of what the type checker currently records;
 *  - **integration** tests run a real mismatch through [PyTypeChecker.explainMismatch] and assert the same prose,
 *    proving the steps are actually attached along the stable protocol/union/callable paths.
 *
 * Assertions use the plain [ProblemMessage.description] (code spans single-quoted); the clickable-link tooltip
 * form is covered by PyInspectionTooltipLinkTest.
 */
@TestFor(classes = [PyTypeMismatchProse::class, PyTypeChecker::class], issues = ["PY-90555", "PY-80221"])
@Subsystems.Inspections
@Layers.Functional
class PyTypeMismatchProseTest : PyCodeInsightTestCase() {

  // ---- unit: one line per Step kind -------------------------------------------------------------------------

  @Test
  fun `a bare nominal mismatch reads as not assignable`() {
    assertEquals("'int' is not assignable to 'str'",
                 flat(node(PyMismatchStep.Nominal(cp("int"), cp("str")))))
  }

  @Test
  fun `a protocol attribute chain folds into a subject and access path`() {
    val tree = node(PyMismatchStep.Protocol(cp("C")),
                    node(PyMismatchStep.Attribute("a"),
                         node(PyMismatchStep.Nominal(cp("str"), cp("int")))))
    assertEquals("Attribute 'a' of 'C' is 'str', but 'int' was expected", flat(tree))
  }

  @Test
  fun `a missing protocol member names the subject`() {
    val tree = node(PyMismatchStep.Protocol(cp("C")), node(PyMismatchStep.Missing("a")))
    assertEquals("'C' has no attribute 'a'", flat(tree))
  }

  @Test
  fun `a nested attribute chain becomes a dotted path with the outermost subject`() {
    val tree = node(PyMismatchStep.Protocol(cp("ImplOuter")),
                    node(PyMismatchStep.Attribute("inner"),
                         node(PyMismatchStep.Protocol(cp("ImplInner")),
                              node(PyMismatchStep.Attribute("value"),
                                   node(PyMismatchStep.Nominal(cp("str"), cp("int")))))))
    assertEquals("Attribute 'inner.value' of 'ImplOuter' is 'str', but 'int' was expected", flat(tree))
  }

  @Test
  fun `a subjectless type argument mismatch reads as a type argument path`() {
    val tree = node(PyMismatchStep.TypeArgument(1), node(PyMismatchStep.Nominal(cp("str"), cp("int"))))
    assertEquals("Type argument '[1]' is 'str', but 'int' was expected", flat(tree))
  }

  @Test
  fun `a return type mismatch reads as a return type`() {
    val tree = node(PyMismatchStep.Return, node(PyMismatchStep.Nominal(cp("int"), cp("str"))))
    assertEquals("The return type is 'int', but 'str' was expected", flat(tree))
  }

  @Test
  fun `an invariant type parameter is explained with the subtype clause`() {
    val tree = node(PyMismatchStep.Invariant("_T", cp("list"), cp("bool"), cp("int")))
    assertEquals("'list' is invariant in '_T', so 'bool' cannot be used where 'int' is required, " +
                 "even though 'bool' is a subtype of 'int'", flat(tree))
  }

  @Test
  fun `a contravariant named parameter is explained as too narrow`() {
    val tree = node(PyMismatchStep.ContravariantParameter("cb", null, plainType("object"), plainType("int")))
    assertEquals("Parameter 'cb' is called with 'object', but only accepts 'int'", flat(tree))
  }

  @Test
  fun `a contravariant positional parameter is identified by its index`() {
    val tree = node(PyMismatchStep.ContravariantParameter(null, 2, plainType("object"), plainType("int")))
    assertEquals("Parameter 2 is called with 'object', but only accepts 'int'", flat(tree))
  }

  @Test
  fun `a contravariant parameter recurses into why the two types are incompatible`() {
    // The critical case: the parameter types A and B are themselves incompatible; the frame must not swallow that.
    val tree = node(PyMismatchStep.ContravariantParameter(null, 2, plainType("A"), plainType("B")),
                    node(PyMismatchStep.Protocol(cp("A")),
                         node(PyMismatchStep.Attribute("a"),
                              node(PyMismatchStep.Nominal(cp("int"), cp("str"))))))
    assertEquals("""
      Parameter 2 is called with 'A', but only accepts 'B'
        Attribute 'a' of 'A' is 'int', but 'str' was expected
    """.trimIndent(), flat(tree))
  }

  @Test
  fun `a contravariant parameter marks protocol types so the structural check is clear`() {
    // The step is built with the types already marked "protocol X" (done by PyTypeChecker for protocols), so two
    // differently-named protocols don't read as arbitrarily-incompatible names.
    val tree = node(PyMismatchStep.ContravariantParameter(null, 2, protocolType("A"), protocolType("B")),
                    node(PyMismatchStep.Protocol(cp("A")),
                         node(PyMismatchStep.Attribute("a"),
                              node(PyMismatchStep.Nominal(cp("int"), cp("str"))))))
    assertEquals("""
      Parameter 2 is called with protocol 'A', but only accepts protocol 'B'
        Attribute 'a' of 'A' is 'int', but 'str' was expected
    """.trimIndent(), flat(tree))
  }

  @Test
  fun `a contravariant parameter drops a trivial nested restatement`() {
    // When the parameter types are plainly unrelated, the nested "str is not assignable to int" just restates the
    // frame line, so it is dropped — leaving the single "is called with / only accepts" line.
    val tree = node(PyMismatchStep.ContravariantParameter(null, 1, plainType("int"), plainType("str")),
                    node(PyMismatchStep.Nominal(cp("str"), cp("int"))))
    assertEquals("Parameter 1 is called with 'int', but only accepts 'str'", flat(tree))
  }

  @Test
  fun `an invariant container suggests a covariant read-only alternative`() {
    val tree = node(PyMismatchStep.Invariant("_T", cp("list"), cp("bool"), cp("int"), "Sequence"))
    assertEquals("'list' is invariant in '_T', so 'bool' cannot be used where 'int' is required, " +
                 "even though 'bool' is a subtype of 'int'; use 'Sequence' if you only read from it", flat(tree))
  }

  @Test
  fun `an invariant dict value suggests Mapping`() {
    val prose = proseFor("""
      expected: dict[str, int]
      actual: dict[str, bool]
    """)
    assertContainsOrdered(prose, "is invariant in", "use 'Mapping' if you only read from it")
  }

  @Test
  fun `an invariant dict key does not suggest Mapping`() {
    // `Mapping` is invariant in its key type too, so it would not fix the mismatch.
    val prose = proseFor("""
      expected: dict[int, str]
      actual: dict[bool, str]
    """)
    assertContainsOrdered(prose, "is invariant in")
    assertFalse("Mapping" in prose, prose)
  }

  @Test
  fun `an invariant set suggests AbstractSet`() {
    val prose = proseFor("""
      expected: set[int]
      actual: set[bool]
    """)
    assertContainsOrdered(prose, "is invariant in", "use 'AbstractSet' if you only read from it")
  }

  @Test
  fun `a typed dict missing key names the owner`() {
    val tree = node(PyMismatchStep.TypedDictKey(cp("Movie"), "year", TypedDictKeyProblem.MISSING, null, null))
    assertEquals("'Movie' has no key 'year'", flat(tree))
  }

  @Test
  fun `a typed dict wrong value type names the key and both types`() {
    val tree = node(PyMismatchStep.TypedDictKey(cp("Movie"), "year", TypedDictKeyProblem.VALUE_TYPE, cp("str"), cp("int")))
    assertEquals("Key 'year' of 'Movie' is 'str', but 'int' was expected", flat(tree))
  }

  @Test
  fun `a mismatch inside a parameterized attribute drills into the type arguments readably`() {
    // The reported nonsense was an 'a[1][1]' path; instead report the attribute's full type and then drill in with
    // a readable "expected …, got …" line per nesting level, down to the innermost divergence.
    val tree = node(PyMismatchStep.NoUnionMember(cp("C"), cp("A | B")),
                    node(PyMismatchStep.Protocol(cp("C"), cp("A")),
                         node(PyMismatchStep.Attribute("a", cp("list[set[None]]"), cp("list[set[int]]")),
                              node(PyMismatchStep.TypeArgument(1, cp("set[None]"), cp("set[int]")),
                                   node(PyMismatchStep.TypeArgument(1, cp("None"), cp("int")),
                                        node(PyMismatchStep.Nominal(cp("None"), cp("int"))))))),
                    message = "not assignable to any member")
    assertEquals("""
      not assignable to any member
        'A' needs 'a' to be 'list[set[int]]', but 'C' has 'list[set[None]]'
          expected 'set[int]', but got 'set[None]'
            expected 'int', but got 'None'
    """.trimIndent(), flat(tree))
  }

  @Test
  fun `a union branch names the member each arm failed against`() {
    val tree = node(PyMismatchStep.NoUnionMember(cp("Thing"), cp("HasName | HasId")),
                    node(PyMismatchStep.Protocol(cp("Thing"), cp("HasName")),
                         node(PyMismatchStep.Attribute("name"),
                              node(PyMismatchStep.Nominal(cp("int"), cp("str"))))),
                    node(PyMismatchStep.Protocol(cp("Thing"), cp("HasId")), node(PyMismatchStep.Missing("id"))),
                    message = "no member of the union matched")
    assertEquals("""
      no member of the union matched
        'HasName' needs 'name' to be 'str', but 'Thing' has 'int'
        'Thing' lacks attribute 'id', which 'HasId' requires
    """.trimIndent(), flat(tree))
  }

  @Test
  fun `an attribute frame that decided on its own keeps its message`() {
    // e.g. a read-only-vs-writable failure records the attribute frame with no child type reason.
    val tree = node(PyMismatchStep.Attribute("x"), message = "Attribute 'x' is not writable")
    assertEquals("Attribute 'x' is not writable", flat(tree))
  }

  @Test
  fun `a node without a step falls back to its own message and recurses`() {
    val tree = node(null,
                    node(PyMismatchStep.Nominal(cp("str"), cp("int"))),
                    message = "'A' is not assignable to 'B'")
    assertEquals("""
      'A' is not assignable to 'B'
        'str' is not assignable to 'int'
    """.trimIndent(), flat(tree))
  }

  // ---- integration: the steps are actually attached along the real match paths ------------------------------

  @Test
  fun `protocol attribute mismatch renders as prose end to end`() {
    val prose = proseFor("""
      from typing import Protocol
      class A(Protocol):
          a: int
      class C:
          a: str
      expected: A
      actual = C()
    """)
    assertEquals("Attribute 'a' of 'C' is 'str', but 'int' was expected", prose)
  }

  @Test
  fun `missing protocol member renders as prose end to end`() {
    val prose = proseFor("""
      from typing import Protocol
      class A(Protocol):
          a: int
      class C:
          b: int
      expected: A
      actual = C()
    """)
    assertEquals("'C' has no attribute 'a'", prose)
  }

  @Test
  fun `a missing protocol method names it as a method end to end`() {
    // A callable protocol member reads "has no method" rather than "has no attribute".
    val prose = proseFor("""
      from typing import Protocol
      class A(Protocol):
          def m(self) -> int: ...
      class C:
          x: int
      expected: A
      actual = C()
    """)
    assertEquals("'C' has no method 'm'", prose)
  }

  @Test
  fun `all incompatible protocol members render as arms end to end`() {
    val prose = proseFor("""
      from typing import Protocol
      class P(Protocol):
          x: int
          y: int
      class A:
          x = ""
      expected: P
      actual = A()
    """)
    assertEquals("""
      'A' is incompatible with protocol 'P'
        Attribute 'x' of 'A' is 'str', but 'int' was expected
        'A' has no attribute 'y'
    """.trimIndent(), prose)
  }

  @Test
  fun `an actual-side union flattens each self-naming member arm end to end`() {
    // A union value assigned where a protocol is expected: every member fails the same way (missing 'y'). Each arm
    // self-names via the member subject, so the "'M' is not assignable to 'P'" wrapper is dropped and the reason
    // folds to one line per member.
    val prose = proseFor("""
      from typing import Protocol
      class P(Protocol):
          x: int
          y: int
      class A:
          x = 1
      class B:
          x = 1
      expected: P
      actual = A() or B()
    """)
    assertEquals("""
      Not all members of 'A | B' are assignable to 'P'
        'A' lacks attribute 'y', which 'P' requires
        'B' lacks attribute 'y', which 'P' requires
    """.trimIndent(), prose)
  }

  @Test
  fun `an actual-side union keeps the member wrapper when the reason does not self-name end to end`() {
    // Two callables that differ only by a parameter name: the reason ("expected a parameter named 'a'") doesn't say
    // which callable, so the "'(b: int) -> None' is not assignable to 'C'" wrapper is kept above each reason.
    val prose = proseFor("""
      from typing import Protocol
      def f3(b: int): ...
      def f4(c: int): ...
      class C(Protocol):
          def __call__(self, a: int): ...
      expected: C
      actual = f3 or f4
    """)
    assertEquals("""
      Not all members of '(b: int) -> None | (c: int) -> None' are assignable to 'C'
        '(b: int) -> None' is not assignable to 'C'
          Expected a parameter named 'a', but found 'b'
        '(c: int) -> None' is not assignable to 'C'
          Expected a parameter named 'a', but found 'c'
    """.trimIndent(), prose)
  }

  @Test
  fun `an actual-side union drops the wrapper for a member with its own protocol header end to end`() {
    // 'A' fails one way (missing 'y') and folds to a flat arm; 'B' fails two ways (wrong 'x' AND missing 'y'), so it
    // keeps a protocol header with two arms. That header ("'B' is incompatible with protocol 'P'") already names the
    // member, so the "'B' is not assignable to 'P'" wrapper is dropped — no redundant restating line.
    val prose = proseFor("""
      from typing import Protocol
      class P(Protocol):
          x: int
          y: int
      class A:
          x = 1
      class B:
          x = ""
      expected: P
      actual = A() or B()
    """)
    assertEquals("""
      Not all members of 'A | B' are assignable to 'P'
        'A' lacks attribute 'y', which 'P' requires
        'B' is incompatible with protocol 'P'
          Attribute 'x' of 'B' is 'str', but 'int' was expected
          'B' has no attribute 'y'
    """.trimIndent(), prose)
  }

  @Test
  fun `a deeply nested protocol mismatch folds to one access-path line end to end`() {
    val prose = proseFor("""
      from typing import Protocol
      class Inner(Protocol):
          value: int
      class Outer(Protocol):
          inner: Inner
      class ImplInner:
          value: str
      class ImplOuter:
          inner: ImplInner
      expected: Outer
      actual: ImplOuter
    """)
    assertEquals("Attribute 'inner.value' of 'ImplOuter' is 'str', but 'int' was expected", prose)
  }

  @Test
  fun `union mismatch drops the trivial per-member leaves end to end`() {
    // 'float' is simply none of the members, so each "'float' is not assignable to 'int'/'str'" leaf only restates
    // the header — they are dropped, leaving one line.
    val prose = proseFor("""
      expected: int | str
      actual = 1.5
    """)
    assertEquals("'float' is not assignable to any member of 'int | str'", prose)
  }

  @Test
  fun `a union of protocols names the member each arm failed against end to end`() {
    // The reported case: 'C' is neither 'A' nor 'B'; without naming the member, the two arms differ only by the
    // expected type ('int' vs 'str') and the reader can't tell which is which.
    val prose = proseFor("""
      from typing import Protocol
      class A(Protocol):
          a: int
      class B(Protocol):
          a: str
      class C:
          a: None
      expected: A | B
      actual = C()
    """)
    assertEquals("""
      'C' is not assignable to any member of 'A | B'
        'A' needs 'a' to be 'int', but 'C' has 'None'
        'B' needs 'a' to be 'str', but 'C' has 'None'
    """.trimIndent(), prose)
  }

  @Test
  fun `a union arm with a parameterized attribute shows the whole attribute type end to end`() {
    // The reported case: the mismatch is deep inside 'list[set[...]]'; the arm must show the attribute's full type,
    // not an 'a[1][1]' path (and with the correct direction, which the invariant type-argument leg would flip).
    val prose = proseFor("""
      from typing import Protocol
      class A(Protocol):
          a: list[set[int]]
      class B(Protocol):
          a: list[set[str]]
      class C:
          a: list[set[None]]
      expected: A | B
      actual = C()
    """)
    assertEquals("""
      'C' is not assignable to any member of 'A | B'
        'A' needs 'a' to be 'list[set[int]]', but 'C' has 'list[set[None]]'
          expected 'set[int]', but got 'set[None]'
            expected 'int', but got 'None'
        'B' needs 'a' to be 'list[set[str]]', but 'C' has 'list[set[None]]'
          expected 'set[str]', but got 'set[None]'
            expected 'str', but got 'None'
    """.trimIndent(), prose)
  }

  @Test
  fun `a typed dict value type mismatch renders as prose end to end`() {
    val prose = proseFor("""
      from typing import TypedDict
      class Movie(TypedDict):
          name: str
          year: int
      class Film(TypedDict):
          name: str
          year: str
      expected: Movie
      actual: Film
    """)
    assertEquals("Key 'year' of 'Movie' is 'str', but 'int' was expected", prose)
  }

  @Test
  fun `a typed dict missing key renders as prose end to end`() {
    val prose = proseFor("""
      from typing import TypedDict
      class Movie(TypedDict):
          name: str
          year: int
      class Film(TypedDict):
          name: str
      expected: Movie
      actual: Film
    """)
    assertEquals("'Movie' has no key 'year'", prose)
  }

  @Test
  fun `a contravariant callable parameter is explained end to end`() {
    val prose = proseFor("""
      from typing import Callable
      def handler(x: int) -> None: ...
      expected: Callable[[object], None]
      actual = handler
    """)
    assertContainsOrdered(prose, "is called with 'object'", "only accepts 'int'")
  }

  @Test
  fun `a contravariant callable parameter is identified by its position end to end`() {
    // The reported case: only the second parameter conflicts, so the breakdown must say which one. The return type
    // is also wrong here (int vs str), and the breakdown now explains both instead of stopping at the parameter.
    val prose = proseFor("""
      from typing import Callable
      expected: Callable[[str, str], str]
      actual: Callable[[str, int], int]
    """)
    assertEquals("""
      Parameter 2 is called with 'str', but only accepts 'int'
      The return type is 'int', but 'str' was expected
    """.trimIndent(), prose)
  }

  @Test
  fun `a callable wrong in both a parameter and its return explains both end to end`() {
    val prose = proseFor("""
      from typing import Callable
      expected: Callable[[str], int]
      actual: Callable[[int], str]
    """)
    assertEquals("""
      Parameter 1 is called with 'str', but only accepts 'int'
      The return type is 'str', but 'int' was expected
    """.trimIndent(), prose)
  }

  @Test
  fun `a contravariant parameter explains why the parameter types conflict end to end`() {
    // The critical reported case: the conflicting parameter types are protocols, so the breakdown must go on to
    // explain *why* 'A' and 'B' are incompatible instead of stopping at "is called with A, but only accepts B".
    val prose = proseFor("""
      from typing import Callable, Protocol
      class A(Protocol):
          a: int
      class B(Protocol):
          a: str
      expected: Callable[[str, A], str]
      actual: Callable[[str, B], str]
    """)
    // 'A' and 'B' are protocols, so the line marks them as such: otherwise two differently-named types read like
    // arbitrarily-incompatible names, and it isn't clear the check is structural (explained by the nested line).
    assertEquals("""
      Parameter 2 is called with protocol 'A', but only accepts protocol 'B'
        Attribute 'a' of 'A' is 'int', but 'str' was expected
    """.trimIndent(), prose)
  }

  // ---- call-site framing: requirement/actual one-liner ------------------------------------------------------

  @Test
  fun `call-site framing explains a missing attribute on one line`() {
    val cs = callSite(callee = "_update_default", argument = "to_app_page()",
                      parameterName = "items", parameterType = "Iterable[str]", actualType = "Page")
    val tree = node(PyMismatchStep.Protocol(cp("Page")), node(PyMismatchStep.Missing("__iter__")))
    assertEquals(
      "'_update_default()' needs parameter 'items' of type 'Iterable[str]' to have attribute '__iter__', " +
      "but 'to_app_page()' is 'Page', which has no '__iter__'",
      framed(cs, tree))
  }

  @Test
  fun `call-site framing explains a wrong attribute type on one line`() {
    val cs = callSite(callee = "register", argument = "Item()",
                      parameterName = "c", parameterType = "Comparable", actualType = "Item")
    val tree = node(PyMismatchStep.Protocol(cp("Item")),
                    node(PyMismatchStep.Attribute("value"),
                         node(PyMismatchStep.Nominal(cp("str"), cp("int")))))
    assertEquals(
      "'register()' needs parameter 'c' of type 'Comparable' to have 'value' of type 'int', " +
      "but 'Item()' is 'Item', whose 'value' is 'str'",
      framed(cs, tree))
  }

  @Test
  fun `call-site framing explains a plain nominal mismatch on one line`() {
    val cs = callSite(callee = "f", argument = "x", parameterName = "n", parameterType = "int", actualType = "str")
    val tree = node(PyMismatchStep.Nominal(cp("str"), cp("int")))
    assertEquals("'f()' needs parameter 'n' of type 'int', but 'x' is 'str'", framed(cs, tree))
  }

  @Test
  fun `call-site framing drops the actual clause for a literal argument`() {
    // The reported case: x("a") for def x(t: int). The argument expression '"a"' already shows the value, so the
    // "but '"a"' is 'Literal["a"]'" clause would only restate it — it is dropped, leaving one requirement line.
    val cs = callSite(callee = "x", argument = "\"a\"", parameterName = "t", parameterType = "int",
                      actualType = "Literal[\"a\"]", actualIsLiteral = true)
    val tree = node(PyMismatchStep.Nominal(cp("Literal[\"a\"]"), cp("int")))
    assertEquals("'x()' needs parameter 't' of type 'int'", framed(cs, tree))
  }

  @Test
  fun `call-site framing drops trivial union members`() {
    // The reported case: def f(a: int | str | bool); f(print()). 'None' is trivially none of the members, so the
    // three "None is not assignable to int/str/bool" leaves are dropped, leaving one requirement line.
    val cs = callSite(callee = "f", argument = "print()",
                      parameterName = "a", parameterType = "int | str | bool", actualType = "None")
    val tree = node(PyMismatchStep.NoUnionMember(cp("None"), cp("int | str | bool")),
                    node(PyMismatchStep.Nominal(cp("None"), cp("int"))),
                    node(PyMismatchStep.Nominal(cp("None"), cp("str"))),
                    node(PyMismatchStep.Nominal(cp("None"), cp("bool"))))
    assertEquals("'f()' needs parameter 'a' to match 'int | str | bool', but 'print()' is 'None', which matches no member",
                 framed(cs, tree))
  }

  @Test
  fun `call-site framing keeps a structurally-failing union member`() {
    val cs = callSite(callee = "f", argument = "Thing()",
                      parameterName = "x", parameterType = "HasName | HasId", actualType = "Thing")
    val tree = node(PyMismatchStep.NoUnionMember(cp("Thing"), cp("HasName | HasId")),
                    node(PyMismatchStep.Nominal(cp("Thing"), cp("HasId"))), // trivial: dropped
                    node(PyMismatchStep.Protocol(cp("Thing"), cp("HasName")), // structural: kept
                         node(PyMismatchStep.Missing("name"))))
    assertEquals("""
      'f()' needs parameter 'x' to match 'HasName | HasId', but 'Thing()' is 'Thing', which matches no member
        'Thing' lacks attribute 'name', which 'HasName' requires
    """.trimIndent(), framed(cs, tree))
  }

  @Test
  fun `call-site framing names the required type plainly when the argument is the union`() {
    // The reversed direction of `NoUnionMember`: a union ARGUMENT against a single (non-union) parameter, e.g.
    // def f(x: int); y: str | bytes; f(y). The required `int` has no members, so "which matches no member" would be
    // nonsense — the requirement names `int` plainly and the actual line shows the union. Both trivial members drop.
    val cs = callSite(callee = "f", argument = "y", parameterName = "x", parameterType = "int", actualType = "str | bytes")
    val tree = node(PyMismatchStep.NoUnionMember(cp("str | bytes"), cp("int"), actualIsUnion = true),
                    node(PyMismatchStep.UnionMember(cp("str")), node(PyMismatchStep.Nominal(cp("str"), cp("int")))),
                    node(PyMismatchStep.UnionMember(cp("bytes")), node(PyMismatchStep.Nominal(cp("bytes"), cp("int")))))
    val prose = framed(cs, tree)!!
    assertEquals("'f()' needs parameter 'x' of type 'int', but 'y' is 'str | bytes'", prose)
    assertFalse("matches no member" in prose, "a non-union required type must not be described as having members: $prose")
  }

  @Test
  fun `call-site framing keeps a structurally-failing member when the argument is the union`() {
    // Same reversed direction, but one union member fails STRUCTURALLY (a missing protocol attribute), so it is kept
    // as a member-named arm beneath the plainly-named required type.
    val cs = callSite(callee = "f", argument = "v", parameterName = "x", parameterType = "P", actualType = "A | B")
    val tree = node(PyMismatchStep.NoUnionMember(cp("A | B"), cp("P"), actualIsUnion = true),
                    node(PyMismatchStep.UnionMember(cp("A")), node(PyMismatchStep.Nominal(cp("A"), cp("P")))), // trivial: dropped
                    node(PyMismatchStep.UnionMember(cp("B")), // structural: kept
                         node(PyMismatchStep.Protocol(cp("B"), cp("P")),
                              node(PyMismatchStep.Missing("name")))))
    assertEquals("""
      'f()' needs parameter 'x' of type 'P', but 'v' is 'A | B'
        'B' lacks attribute 'name', which 'P' requires
    """.trimIndent(), framed(cs, tree))
  }

  @Test
  @TestFor(issues = ["PY-90555"])
  fun `a union argument against a single parameter is framed correctly end to end`() {
    // The full path: a union-typed argument whose members all fail against a single (non-union) parameter must NOT
    // read "which matches no member" — that phrasing is only correct when the REQUIRED type is the union.
    val tooltip = argumentTooltip("""
      def f(x: int) -> None: ...
      y: str | bytes
      f(y)
    """)
    assertContainsOrdered(tooltip, "f()", "needs parameter", "<code>y</code>")
    assertFalse("matches no member" in tooltip, "the required 'int' is not a union, so 'matches no member' is wrong: $tooltip")
  }

  @Test
  fun `call-site framing uses an unnamed phrasing for positional parameters`() {
    val cs = callSite(callee = "f", argument = "x", parameterName = null, parameterType = "int", actualType = "str")
    val tree = node(PyMismatchStep.Nominal(cp("str"), cp("int")))
    assertEquals("'f()' needs an argument of type 'int', but 'x' is 'str'", framed(cs, tree))
  }

  @Test
  fun `call-site framing falls back when there is no callee`() {
    val cs = callSite(callee = null, argument = "x", parameterName = "n", parameterType = "int", actualType = "str")
    assertNull(PyTypeMismatchProse.argumentProseLines(cs, node(PyMismatchStep.Nominal(cp("str"), cp("int")))))
  }

  @Test
  fun `call-site framing prepends the call-site line before an educational variance leaf`() {
    // Invariance is self-contained, but the reader still wants to know which call and argument triggered it, so the
    // call-site line is prepended and the educational leaf kept beneath it.
    val cs = callSite(callee = "f", argument = "xs", parameterName = "x", parameterType = "list[int]", actualType = "list[bool]")
    assertEquals("""
      'f()' needs parameter 'x' of type 'list[int]', but 'xs' is 'list[bool]'
        'list' is invariant in '_T', so 'bool' cannot be used where 'int' is required, even though 'bool' is a subtype of 'int'
    """.trimIndent(),
                 framed(cs, node(PyMismatchStep.Invariant("_T", cp("list"), cp("bool"), cp("int")))))
  }

  @Test
  fun `call-site framing prepends the call-site line before a contravariant parameter`() {
    // The contravariant frame + its nested reason are kept, under a headline that names the call and argument.
    val cs = callSite(callee = "register", argument = "cb", parameterName = "cb",
                      parameterType = "Callable[[object], None]", actualType = "Callable[[int], None]")
    val tree = node(PyMismatchStep.ContravariantParameter(null, 1, plainType("object"), plainType("int")),
                    node(PyMismatchStep.Nominal(cp("int"), cp("object"))))
    assertEquals("""
      'register()' needs parameter 'cb' of type 'Callable[[object], None]', but 'cb' is 'Callable[[int], None]'
        Parameter 1 is called with 'object', but only accepts 'int'
    """.trimIndent(), framed(cs, tree))
  }

  @Test
  fun `call-site framing explains a contravariant callable argument end to end`() {
    // handler only accepts int, but f may call its callback with any object: the plain breakdown surfaces it (a
    // contravariant parameter isn't fused into the "f() needs …" framing, so we get the headline + this line).
    val prose = proseFor("""
      from typing import Callable
      def handler(x: int) -> None: ...
      def f(cb: Callable[[object], None]) -> None: ...
      expected: Callable[[object], None]
      actual = handler
    """)
    assertContainsOrdered(prose, "is called with 'object'", "only accepts 'int'")
  }

  @Test
  fun `argument mismatch is framed from the call site end to end`() {
    // Full path through the inspection: the tooltip names the callee, parameter and argument.
    val tooltip = argumentTooltip("""
      def f(a: int | str | bool) -> None: ...
      f(print())
    """)
    // Types render as separate clickable <code> spans, so assert on the framing words + the individual members.
    // The callee is a clickable link to the function; the second line names the argument expression (print()).
    assertContainsOrdered(tooltip, "#element/", "f()", "needs parameter", "<code>print()</code>", "matches no member")
    assertContainsOrdered(tooltip, "builtins.int", "builtins.str", "builtins.bool", "NoneType")
    assertFalse("not assignable" in tooltip, "trivial per-member leaves must be dropped: $tooltip")
  }

  @Test
  fun `a literal argument drops its redundant type clause end to end`() {
    // The reported case: x("a") for def x(t: int). The tooltip keeps only the requirement — the argument expression
    // already shows the value, so "'"a"' is 'Literal["a"]'" would only restate it.
    val tooltip = argumentTooltip("""
      def x(t: int) -> int: ...
      x("a")
    """)
    assertContainsOrdered(tooltip, "x()", "needs parameter", "builtins.int")
    assertFalse("Literal" in tooltip, "a literal argument's type clause must be dropped: $tooltip")
    assertFalse(", but " in tooltip, "no actual clause should be joined for a literal: $tooltip")
  }

  // ---- tree guide lines (tooltip-only) ----------------------------------------------------------------------

  @Test
  fun `a multi-arm branch draws directory-tree connectors`() {
    // A callable wrong in both its parameter and its return: the two arms are parallel, so they get connectors.
    val cs = callSite(callee = "f", argument = "x", parameterName = "cb",
                      parameterType = "(str) -> int", actualType = "(int) -> str")
    val root = node(PyMismatchStep.Combined,
                    node(PyMismatchStep.ContravariantParameter(null, 1, plainType("str"), plainType("int"))),
                    node(PyMismatchStep.Return, node(PyMismatchStep.Nominal(cp("str"), cp("int")))))
    val tooltip = tooltip(PyTypeMismatchProse.argumentProseLines(cs, root)!!)
    // The connector precedes its line's text: '├─' the parameter arm, '└─' the (last) return arm.
    assertContainsOrdered(tooltip, "├─", "is called with", "└─", "The return type")
  }

  @Test
  fun `a single-arm descent stays plain with no connectors`() {
    // A lone "because" chain (contravariant parameter -> its one nested reason) is prose, not a branch.
    val cs = callSite(callee = "f0", argument = "x", parameterName = "a",
                      parameterType = "(str, A) -> str", actualType = "(str, B) -> str")
    val root = node(PyMismatchStep.ContravariantParameter(null, 2, protocolType("A"), protocolType("B")),
                    node(PyMismatchStep.Protocol(cp("A")),
                         node(PyMismatchStep.Attribute("a"),
                              node(PyMismatchStep.Nominal(cp("int"), cp("str"))))))
    val tooltip = tooltip(PyTypeMismatchProse.argumentProseLines(cs, root)!!)
    assertFalse("├" in tooltip || "└" in tooltip, "a single-arm descent must not draw connectors: $tooltip")
  }

  // ---- helpers ----------------------------------------------------------------------------------------------

  private fun tooltip(lines: List<PyTypeMismatchProse.ProseLine>): String {
    val builder = HtmlBuilder()
    PyTypeMismatchProse.appendLines(builder, lines, leadingBreak = false)
    return builder.toString()
  }

  private fun cp(@NlsSafe name: String): CodifiedParam = CodifiedParam(name, "<code>$name</code>")

  /** A contravariant-line type span, pre-rendered as PyTypeChecker builds it: the type in a code span... */
  private fun plainType(@NlsSafe name: String): CodifiedParam = CodifiedParam("'$name'", "<code>$name</code>")

  /** ...and, for a protocol, the plain word "protocol" before that code span (only the type is coded). */
  private fun protocolType(@NlsSafe name: String): CodifiedParam = CodifiedParam("protocol '$name'", "protocol <code>$name</code>")

  private fun node(
    step: PyMismatchStep?,
    vararg children: PyTypeMismatchExplanation,
    message: String = "<intermediate>",
  ): PyTypeMismatchExplanation = PyTypeMismatchExplanation(ProblemMessage(message, message), children.toList(), step)

  private fun flat(vararg roots: PyTypeMismatchExplanation): String =
    PyTypeMismatchProse.proseLines(roots.toList()).joinToString("\n") { "  ".repeat(it.depth) + it.message.description }

  private fun callSite(
    callee: String?,
    argument: String,
    parameterName: String?,
    parameterType: String,
    actualType: String,
    actualIsLiteral: Boolean = false,
  ): PyTypeMismatchProse.CallSite =
    PyTypeMismatchProse.CallSite(callee?.let { cp("$it()") }, argument, parameterName, cp(parameterType), cp(actualType), actualIsLiteral)

  private fun framed(callSite: PyTypeMismatchProse.CallSite, root: PyTypeMismatchExplanation): String? =
    PyTypeMismatchProse.argumentProseLines(callSite, root)
      ?.joinToString("\n") { "  ".repeat(it.depth) + it.message.description }

  private fun argumentTooltip(@Language("Python") code: String): String {
    val inspection = PyTypeCheckerInspection()
    myFixture.enableInspections(inspection)
    try {
      myFixture.configureByText("a.py", code.trimIndent())
      val info = myFixture.doHighlighting().single { it.description?.contains("instead") == true }
      return info.toolTip!!
    }
    finally {
      myFixture.disableInspections(inspection)
    }
  }

  private fun proseFor(@Language("Python") code: String): String {
    myFixture.configureByText("a.py", code.trimIndent())
    val explanation = runReadActionBlocking {
      val expected = myFixture.findElementByText("expected", PyTargetExpression::class.java)
      val actual = myFixture.findElementByText("actual", PyTargetExpression::class.java)
      val context = TypeEvalContext.codeAnalysis(myFixture.project, myFixture.file)
      PyTypeChecker.explainMismatch(context.getType(expected), context.getType(actual), context, actual)
    }
    assertNotNull(explanation, "Expected a breakdown but the types matched")
    return flat(explanation!!)
  }

  private fun assertContainsOrdered(text: String, vararg fragments: String) {
    var from = 0
    for (fragment in fragments) {
      val index = text.indexOf(fragment, from)
      assert(index >= 0) { "Expected to find '$fragment' after index $from in:\n$text" }
      from = index + fragment.length
    }
  }
}
