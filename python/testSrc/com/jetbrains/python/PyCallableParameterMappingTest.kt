// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext

class PyCallableParameterMappingTest : PyTestCase() {

  fun testEmpty() {
    checkMatch("()", "()")
    checkMatch("(/)", "(/)")
    checkMatch("()", "(/)")
    checkMatch("(/)", "()")
  }

  fun testSimple() {
    checkMatch("(a)", "(a)")
    checkMatch("(a, b, c)", "(a, b, c)")
    checkNotMatch("(a: int)", "(b: int)")
    checkMatch("(a: int)", "(a: int, b: str = 'default')")
  }

  fun testKeywordOnlyVsStandard() {
    checkMatch("(*, a: int)", "(a: int)")
    checkNotMatch("(*, a: int)", "(b: int)") // extra b, missing a (names don't match)
    checkNotMatch("(*, a: int)", "(a: str)") // names are OK, but types are incompatible
    checkNotMatch("(*, a: int, b: str)", "(a: int)") // missing parameter b
    checkNotMatch("(*, a: int)", "(a: int, b: str)") // extra parameter b
    checkNotMatch("(*, a: int, b: str)", "(a: int, b: str, c: float)") // extra parameter c
    checkMatch("(*, a: int, b: str)", "(a: int, b: str)") // OK
    checkMatch("(*, b: str, a: int)", "(a: int, b: str)") // OK, order is not important
    checkMatch("(*, a: int, b: str)", "(b: str, a: int)") // OK, order is not important
    checkNotMatch("(*, b: int, a: str)", "(x: str, y: int)") // Names don't match
  }

  fun testKeywordOnlyVsStandardWithDefaults() {
    checkMatch("(*, a: int)", "(a: int = 1)") // OK
    checkMatch("(*, a: int)", "(a: int, b: str = 'str')") // OK, b ignored
    checkMatch("(*, a: int, b: str)", "(a: int, b: str, c: float = 1.0)") // OK, c ignored
    checkNotMatch("(*, a: int, b: str = 'str')", "(a: int, b: str)") // b is missing default
    checkMatch("(*, a: int, b: str = 'str')", "(a: int, b: str = 'str')") // OK, both have defaults
    checkNotMatch("(*, a: int = 1, b: str = 'str')", "(a: int, b: str = 'str')") // default in actual a is missing
    checkMatch("(*, a: int = 1, b: str = 'str')", "(a: int = 1, b: str = 'str')") // OK
  }

  fun testKeywordOnlyVsKeywordOnly() {
    checkMatch("(*, a: int, b: str)", "(*, b: str, a: int)") // OK, order is not important
    checkNotMatch("(*, a: int, b: str)", "(*, x: str, y: int)") // Names mismatch
    checkMatch("(*, a: int, b: str)", "(*, a: int, b: str)") // Same signatures
    checkMatch("(*, a: int, b: str, c: float)", "(*, c: float, a: int, b: str)") // OK, order is not important
    checkNotMatch("(*, a: int, b: str, c: float)", "(*, c: float, a: bool, b: str)") // Types mismatch
  }

  fun testKeywordOnlyVsKeywordOnlyWithDefault() {
    checkNotMatch("(*, a: int = 1)", "(a: int)") // a is missing default
    checkMatch("(*, a: int)", "(*, a: int = 1)") // OK
    checkNotMatch("(*, a: int, b: str = 'default')", "(*, a: int, b: str)") // b is missing default
    checkMatch("(*, a: int, b: str)", "(*, a: int, b: str = 'default')") // OK
    checkMatch("(*, a: int = 0, b: str = 'default')", "(*, a: int = 1, b: str = 'other')") // default val is not important
    checkMatch("(*, a: int, b: str)", "(*, a: int, b: str = 'default', c: float = 1.0)") // c is not important unless it has a default
    checkNotMatch("(*, a: int, b: str)", "(*, a: int, b: str = 'default', c: float)") // extra param c
    checkNotMatch("(*, a: int, b: str = 'default', c: float)", "(*, a: int, b: str, c: float)") // b is missing default
    checkNotMatch("(*, a: int, b: str = 'str', c: bool = True)", "(*, a: int)") // b, c are missing
    checkMatch("(*, a: int)", "(*, a: int, b: str = 'str', c: bool = True)") // OK, all missing have defaults
  }

  fun testKeywordOnlyVsPositionalOnly() {
    checkNotMatch("(*, a: int)", "(a: int, /)") // Missing keyword-only parameter a
    checkNotMatch("(*, a: int = 1)", "(a: int, /)") // Missing keyword-only parameter a
    checkNotMatch("(*, a: int = 1)", "(a: int = 1, /)") // Missing keyword-only parameter a
  }

  fun testStandardVsKeywordOnly() {
    checkNotMatch("(a: int)", "(*, a: int)") // extra parameter a
    checkNotMatch("(a: int)", "(*, a: int, b: str)") // extra parameters a, b
    checkNotMatch("(a: int)", "(*, a: int, b: str, c: float)") // extra parameters a, b, c
  }

  fun testStandardVsKeywordOnlyWithDefaults() {
    checkMatch("()", "(x: int = 0)") // OK, x ignored
    checkNotMatch("(a: int = 1)", "(*, a: int)") // extra parameter a
    checkNotMatch("(a: int)", "(*, a: int = 1)") // too many positionals
    checkNotMatch("(a: int = 1)", "(*, a: int = 1)") // too many positionals
  }

  fun testStandardVsStandard() {
    checkNotMatch("()", "(a: int)") // a is missing
    checkMatch("(a: int)", "(a: int)")
    checkMatch("(a: int, b: str)", "(a: int, b: str)")
    checkNotMatch("(a: int, b: str)", "(b: int, a: str)") // names don't match (b vs a, a vs b)
    checkNotMatch("(a: int, b: str)", "(a: str, b: int)") // types don't match
    checkNotMatch("(a: int, b: str)", "()") // too many positionals
    checkNotMatch("()", "(a: int, b: str)") // extra parameters a, b
  }

  fun testStandardVsStandardWithDefaults() {
    checkMatch("(a: int, b: str)", "(a: int, b: str, c: bool = True)") // OK, c ignored
    checkNotMatch("(a: int, b: str, c: bool = True)", "(a: int, b: str)") // expected 2 but received 3
    checkMatch("(a: int = 1, b: str = 'str')", "(a: int = 1, b: str = 'str')") // Ok
    checkNotMatch("(a: int = 1, b: str = 'str')", "(a: int = 1, b: str)") // default in actual b is missing
    checkMatch("(a: int, b: str = 'str')", "(a: int = 1, b: str = 'str')") // OK
    checkMatch("(a: int, b: str)", "(a: int = 1, b: str = 'str')") // OK
  }

  fun testStandardVsPosOnly() {
    checkNotMatch("(a: int)", "(a: int, /)") // a is not pos-only
    checkNotMatch("(a: int, b: str)", "(a: int, /)") // a, b are not pos-only
  }

  fun testStandardVsPosOnlyWithDefaults() {
    checkNotMatch("(a: int)", "(a: int = 1, /)")
    checkNotMatch("(a: int = 1)", "(a: int = 1, /)")
    checkNotMatch("(a: int = 1)", "(a: int, /)")
  }

  fun testPosOnlyVsStandard() {
    checkMatch("(a: int, /)", "(a: int)") // OK
    checkMatch("(a: int, /)", "(b: int)") // OK, name doesn't matter
    checkMatch("(a: int, b: str, /)", "(x: int, y: str)") // OK, names don't matter
    checkNotMatch("(a: int, b: str, /)", "(x: int, y: bool)") // Types don't match
    checkNotMatch("(a: int, b: str, /)", "(x: int)") // Function accepts too many positional parameters; expected 1 but received 2
    checkNotMatch("(a: int, /)", "(x: int, y: str)") // Extra param y
  }

  fun testPosOnlyVsStandardWithDefaults() {
    checkMatch("(a: int, /)", "(a: int = 1)") // OK
    checkMatch("(a: int = 1, /)", "(a: int = 1)") // OK
    checkNotMatch("(a: int = 1, /)", "(a: int)") // a in actual is missing default
    checkMatch("(a: int, b: str, /)", "(x: int, y: str = 'str')") // OK
    checkMatch("(a: int, b: str, /)", "(x: int, y: str = 'str')") // OK
    checkMatch("(a: int, b: str, /)", "(x: int = 1, y: str = 'str')") // OK
    checkMatch("(a: int, b: str = 'str', /)", "(x: int, y: str = 'str')") // OK
    checkNotMatch("(a: int = 1, b: str = 'str', /)", "(x: int, y: str = 'str')") // x is missing default
  }

  fun testPosOnlyVsPosOnly() {
    checkMatch("(/)", "(/)") // OK
    checkMatch("(a: int, /)", "(a: int, /)") // OK
    checkNotMatch("(a: int, /)", "(a: str, /)") // Types mismatch
    checkMatch("(a: int, b: str, /)", "(x: int, y: str, /)") // Names are not important
    checkNotMatch("(a: int, b: str, /)", "(x: int, /)") // Too many positionals
    checkNotMatch("(a: int, b: str, /)", "(x: int, /)") // Too few positionals
  }

  fun testPosOnlyVsPosOnlyWithDefaults() {
    checkMatch("(a: int, /)", "(a: int = 1, /)") // OK
    checkMatch("(a: int = 1, /)", "(a: int = 1, /)") // OK
    checkMatch("(a: int = 1, /)", "(x: int = 1, /)") // OK, names are not important
    checkNotMatch("(a: int = 1, /)", "(a: int, /)") // a is missing default
    checkNotMatch("(a: int = 1, /)", "(x: int, /)") // x is missing default
    checkNotMatch("(a: int, b: str = 'default', /)", "(a: int, b: str, /)") // b is missing default
    checkMatch("(a: int, b: str, /)", "(a: int, b: str = 'default', /)") // OK
  }

  fun testPosOnlyVsKeywordOnly() {
    checkNotMatch("(a: int, /)", "(*, a: int)")
    checkNotMatch("(a: int, b: str, /)", "(*, a: int, b: str)")
    checkNotMatch("(a: int = 1, b: str = 'str', /)", "(*, a: int = 1, b: str = 'str')")
  }

  fun testUnionType() {
    checkMatch("(a: int)", "(a: int | str)")
    checkNotMatch("(a: int | str)", "(a: int)")
  }

  fun testKeywordOnlyWithKwargs() {
    checkNotMatch("(*, a: int, b: str)", "(**kwargs: int)") // types mismatch
    checkMatch("(*, a: int, b: str)", "(**kwargs: int | str)") // OK
    checkMatch("(*, a: int, b: str)", "(*, a: int, **kwargs: str)") // OK
    checkNotMatch("(**kwargs: int | str)", "(*, a: int = 1, **kwargs: str)") // types mismatch
    checkNotMatch("(**kwargs: int | str)", "(**kwargs: int)") // types mismatch
    checkMatch("(*, a: int, **kwargs: str)", "(**kwargs: int | str)") // OK
    checkNotMatch("(*, a: int, **kwargs: str)", "(**kwargs: int)") // types mismatch
    checkMatch("(**kwargs: int)", "(**kwargs: int | str)") // OK
    checkNotMatch("(**kwargs: int)", "(*, a: int = 1, **kwargs: str)") // types mismatch
    checkNotMatch("(a: int, b: str)", "(**kwargs: int | str)") // not keyword-only
    checkNotMatch("(a: int, b: str)", "(*, a: int, **kwargs: str)") // not keyword-only
  }

  fun testStandardVsVararg() {
    checkNotMatch("(a: int, *args)", "(a: int)")
    checkMatch("(a: int)", "(a: int, *args)")
    checkNotMatch("(a: int, *args)", "(a: int, b: str)")
    checkNotMatch("(a: int, b: str)", "(a: int, *args)")
  }

  fun testArgsVsStandardOrPositionalWithArgs() {
    checkMatch("(*args)", "(a = 1, *args)") // OK
    checkNotMatch("(*args)", "(a, *args)") // Missing default
    checkMatch("(*args: int)", "(a: int = 1, *args: int)") // OK
    checkNotMatch("(*args: int)", "(a: int, *args: int)") // Missing default
    checkNotMatch("(*args: int)", "(a: int, b: int = 1, *args: int)") // Missing default
    checkMatch("(*args: int)", "(a: int = 1, b: int = 1, *args: int)") // OK
    checkMatch("(*args: int)", "(a: int = 1, /, *args: int)") // OK
    checkNotMatch("(*args: int)", "(a: int, /, *args: int)") // Missing default
    checkNotMatch("(*args: int)", "(a: int, b: int = 1, /, *args: int)") // Missing default
    checkMatch("(*args: int)", "(a: int = 1, b: int = 1, /, *args: int)") // OK
  }

  fun testKwargsVsStandardOrKeywordOnly() {
    checkMatch("(**kwargs)", "(a = 1, **kwargs)") // OK
    checkNotMatch("(**kwargs)", "(a, **kwargs)") // Missing default
    checkMatch("(**kwargs: int)", "(a: int = 1, **kwargs: int)") // OK
    checkNotMatch("(**kwargs: int)", "(a: int, **kwargs: int)") // Missing default
    checkNotMatch("(**kwargs: int)", "(a: int, b: int = 1, **kwargs: int)") // Missing default
    checkMatch("(**kwargs: int)", "(a: int = 1, b: int = 1, **kwargs: int)") // OK
    checkMatch("(**kwargs: int)", "(*, a: int = 1, **kwargs: int)") // OK
    checkNotMatch("(**kwargs: int)", "(*, a: int, **kwargs: int)") // Missing default
    checkNotMatch("(**kwargs: int)", "(*, a: int, b: int = 1, **kwargs: int)") // Missing default
    checkMatch("(**kwargs: int)", "(*, a: int = 1, b: int = 1, **kwargs: int)") // OK
  }

  fun testMixedPositionalAndKeyword() {
    checkMatch("(a: int, b: str, /, c: float, *, d: bool)", "(a: int, b: str, c: float, d: bool)")
    checkNotMatch("(a: int, b: str, c: float, d: bool)", "(a: int, b: str, /, c: float, *, d: bool)")
  }

  fun testStandardBetweenPosOnlyAndKwOnly() {
    checkMatch("(a: int, /, b: str, *args, c: float)", "(a: int, /, b: str, *args, c: float = 1.0)")
    checkMatch("(a: int, /, b: str, c: str, *args, d: float)", "(a: int, /, b: str, c: str, *args, d: float = 1.0)")
    checkMatch("(a: int, /, b: str, c: str, *args, d: float)", "(a: int, /, b: str, c: str, c1: str = 'str', *args, d: float = 1.0)")
    checkNotMatch("(a: int, /, b: str, *args, c: float = 1.0)", "(a: int, /, b: str, *args, c: float)") // c is missing default
    checkMatch("(a: int, /, b: str = 'str', *args, c: float)", "(a: int, /, b: str = 'str', *args, c: float = 1.0)")
  }

  fun testComplexSignatures() {
    // OK
    checkMatch("(a: int, b: str, /, c: float, *, d: bool)",
               "(a: int, b: str, c: float, d: bool)")
    // **kwargs is missing
    checkNotMatch("(a: int, b: str, /, c: float, *, d: bool, **kwargs)",
                  "(a: int, b: str, c: float, d: bool)")
    // OK
    checkMatch("(a: int, b: str, /, c: float, *, d: bool, **kwargs)",
               "(a: int, b: str, c: float, d: bool, **kwargs)")
    // missing keyword a, b
    checkNotMatch("(a: int, b: str, c: float, d: bool)",
                  "(a: int, b: str, /, c: float, *, d: bool)")
    // extra d
    checkNotMatch("(a: int, b: str, c: float, d: bool)",
                  "(a: int, b: str, /, c: float, *, d: bool, **kwargs)")
    // missing keyword d
    checkNotMatch("(a: int, b: str, /, c: float, *args, d: bool, **kwargs)",
                  "(a: int, b: str, c: float, d: bool)")
    // OK, d is keyword
    checkNotMatch("(a: int, b: str, /, c: float, *args, d: bool, **kwargs)",
                  "(a: int, b: str, c: float, *, d: bool)")
    // OK
    checkMatch("(a: int, b: str, /, c: float, *args, d: bool, **kwargs)",
               "(a: int, b: str, c: float, *args: int, d: bool, **kwargs: str)")
    // type mismatch for d
    checkNotMatch("(a: int, b: str, /, c: float, *args, d: bool, **kwargs)",
                  "(a: int, b: str, c: float, *args: int, d: str, **kwargs: str)")
    // keyword-only e: int from expected vs **kwargs: str types mismatch
    checkNotMatch("(a: int, b: str, /, c: float, *args, d: bool, e: int, **kwargs)",
                  "(a: int, b: str, c: float, *args: int, d: bool, **kwargs: str)")
    // OK, e matches vs kwargs now
    checkMatch("(a: int, b: str, /, c: float, *args, d: bool, e: str, **kwargs)",
               "(a: int, b: str, c: float, *args: int, d: bool, **kwargs: str)")

    // keyword d is missing
    checkNotMatch("(a: int, b: str = 'default', /, c: float = 1.0, *, d: bool = True)", "(a: int)")
    // Default for b is missing
    checkNotMatch("(a: int, /, *, d: bool = True)", "(a: int, b: str = 'default', /, c: float = 1.0, *, d: bool)")
    // OK
    checkMatch("(a: int, /, *, d: bool = True)", "(a: int, b: str = 'default', /, c: float = 1.0, *, d: bool = True)")
    // d: bool vs d: str types mismatch
    checkNotMatch("(a: int, b: str, /, c: float, *, d: bool)", "(a: int, b: str, c: float, *, d: str)")
    // OK
    checkMatch("(a: int, /)", "(a: int, b: str = 'default', /, c: float = 1.0, *, d: bool = True)")
  }

  fun testKwargsTypeCompatibility() {
    checkMatch("(*, a: int, b: str)", "(**kwargs: int | str)")
    checkMatch("(*, a: int, b: str)", "(*, a: int, **kwargs: str)")

    checkNotMatch("(*, a: int, b: str)", "(**kwargs: int)") // str is not a subtype of int
    checkMatch("(*, a: int, b: int)", "(**kwargs: int)")    // int is a subtype of int

    checkMatch("(*, a: int, **kwargs: str)", "(**kwargs: int | str)")
    checkMatch("(**kwargs: int)", "(**kwargs: int | str)")
  }

  fun testArgsTypeCompatibility() {
    checkNotMatch("(*args: int | str)", "(a: int, /, *args: str)")
    checkMatch("(*args: int)", "(a: int = 1, /, *args: int | str)")
    checkNotMatch("(*args: int | str)", "(*args: str)")
    checkNotMatch("(*args: int | str)", "(*args: int)")

    checkMatch("(*args: int)", "(*args: int | str)")
    checkNotMatch("(*args: int)", "(a: int = 1, /, *args: str)")
    checkNotMatch("(*args: int)", "(*args: str)")
  }

  fun testKeyWordOnlyAfterArgs() {
    checkMatch("(*args, a: int, b: str, c: float)", "(*args, a: int = 1, b: str = 'x', c: float = 1.0)")
    checkNotMatch("(*args, a: int = 1, b: str = 'x', c: float = 1.0)", "(*args, a: int, b: str, c: float)") // missing defaults
    checkMatch("(*args, a: int, b: str = 'x', c: float)", "(*args, a: int = 1, b: str = 'y', c: float = 1.0)")
    checkNotMatch("(*args, a: int, b: str, c: float)", "(*args, a: int, b: str, x: float)") // missing c, extra x
  }

  fun testIntStrKwargs() {
    // Test compatibility between **kwargs with different type annotations

    // Union types in **kwargs
    checkNotMatch("(**kwargs: int | str)", "(**kwargs: int)") // int | str is not a subtype of int

    // **kwargs with keyword-only parameters
    checkNotMatch("(**kwargs: int | str)", "(*, a: int = 1, **kwargs: str)")
    checkNotMatch("(**kwargs: int | str)", "(*, a: int = 1, **kwargs: int | str)")
    checkMatch("(**kwargs: int | str)", "(*, a: int | str = 1, **kwargs: int | str)")

    // Same type in both **kwargs
    checkMatch("(**kwargs: int | str)", "(**kwargs: int | str)")
  }

  fun testCallableParameterTypeContravariance() {
    checkMatch("(a: int)", "(a: float)")
    checkNotMatch("(a: float)", "(a: int)")

    // *args type mismatch
    checkNotMatch("(*args: int)", "(a: int = 1, /, *args: str)")
    checkNotMatch("(*args: int)", "(*args: str)")
    checkNotMatch("(*args: int | str)", "(a: int = 1, /, *args: str)")
    checkNotMatch("(*args: int | str)", "(*args: str)")
    checkNotMatch("(*args: int | str)", "(*args: int)")
    checkNotMatch("(a: int, /, *args: str)", "(*args: int)")

    // **kwargs type mismatch
    checkNotMatch("(*, a: int, b: str)", "(**kwargs: int)") // str is not a subtype of int
    checkNotMatch("(**kwargs: int | str)", "(**kwargs: int)") // int | str is not a subtype of int
    checkNotMatch("(**kwargs: int | str)", "(*, a: int = 1, **kwargs: str)")
    checkNotMatch("(**kwargs: int | str)", "(*, a: int = 1, **kwargs: int | str)")
    checkNotMatch("(**kwargs: float)", "(**kwargs: int)")
    checkNotMatch("(*, a: int, **kwargs: str)", "(**kwargs: int)")
    checkNotMatch("(**kwargs: int)", "(*, a: int, **kwargs: str)")

    // *args type mismatch in positional-only context
    checkNotMatch("(a: int, b: str, /)", "(*args: int)")

    // Complex signature with type mismatch in keyword-only parameter
    checkNotMatch("(a: int, b: str, /, c: float, *args, d: bool, **kwargs)",
                  "(a: int, b: str, c: float, *args: int, d: str, **kwargs: str)")
  }

  fun testArgsParameter() {
    // If a callable B has a signature with a *args parameter, callable A
    // must also have a *args parameter to be a subtype of B, and the type of
    // B's *args parameter must be a subtype of A's *args parameter

    checkMatch("()", "(*args: int)")
    checkMatch("()", "(*args: float)")

    checkMatch("(a: int)", "(a: int, *args: str)")
    checkNotMatch("(a: int, *args)", "(a: int)")

    checkNotMatch("(*args: int)", "()")
    checkMatch("(*args: int)", "(*args: float)")

    checkNotMatch("(*args: float)", "()")
    checkNotMatch("(*args: float)", "(*args: int)")
  }

  fun testPositionalOnlyWithArgs() {
    // If a callable B has a signature with one or more positional-only parameters,
    // a callable A is a subtype of B if A has an *args parameter whose type is a
    // supertype of the types of any otherwise-unmatched positional-only parameters in B

    checkNotMatch("(a: int, b: str, /)", "(*args: int)")
    checkMatch("(a: int, b: str, /)", "(*args: int | str)")
    checkMatch("(a: int, b: str, /)", "(a: int, /, *args: str)")

    checkNotMatch("(*args: int | str)", "(a: int, /, *args: str)")
    checkNotMatch("(*args: int | str)", "(*args: int)")
    checkMatch("(a: int, b: str, /, *args: str)", "(*args: int | str)")
    checkMatch("(a: int, /, *args: str)", "(*args: int | str)")
    checkNotMatch("(a: int, /, *args: str)", "(*args: int)")
    checkMatch("(*args: int)", "(*args: int | str)")
    checkNotMatch("(*args: int)", "(a: int, /, *args: str)")

    checkNotMatch("(a: int, b: str)", "(*args: int | str)")
    checkNotMatch("(a: int, b: str)", "(a: int, /, *args: str)")
  }

  fun testKwargsParameter() {
    checkMatch("()", "(**kwargs: int)")
    checkMatch("()", "(**kwargs: float)")

    checkNotMatch("(**kwargs: int)", "()")
    checkMatch("(**kwargs: int)", "(**kwargs: float)")

    checkNotMatch("(**kwargs: float)", "()")
    checkNotMatch("(**kwargs: float)", "(**kwargs: int)")
  }

  fun testEmptyWithArgs() {
    checkMatch("()", "(*args)")
    checkNotMatch("(*args)", "()")
  }

  fun testMixedParametersWithDefault() {
    checkMatch("(a: int, b: str, /)", "(a: int, b: str = 'default', /)") // OK
    checkNotMatch("(a: int, b: str = 'default', /)", "(a: int, b: str, /)") // b is missing default
    checkMatch("(a: int, *, b: str)", "(a: int = 0, *, b: str)") // OK
    checkNotMatch("(a: int = 0, *, b: str)", "(a: int, *, b: str)") // a is missing default
    checkMatch("(a: int, /, b: str, *, c: float)", "(a: int = 0, /, b: str = 'default', *, c: float = 1.0)") // OK
    checkNotMatch("(a: int = 0, /, b: str = 'default', *, c: float = 1.0)",
                  "(a: int, /, b: str, *, c: float)") // a, b, c are missing default
  }

  fun testDefaultWithArgsKwargs() {
    checkMatch("(a: int, *args)", "(a: int = 0, *args)") // OK
    checkNotMatch("(a: int = 0, *args)", "(a: int, *args)") // a is missing default
    checkMatch("(a: int, **kwargs)", "(a: int = 0, **kwargs)") // OK
    checkNotMatch("(a: int = 0, **kwargs)", "(a: int, **kwargs)") // a is missing default
    checkMatch("(*, a: int, **kwargs)", "(*, a: int = 0, **kwargs)") // OK
    checkNotMatch("(*, a: int = 0, **kwargs)", "(*, a: int, **kwargs)") // a is missing default
    checkMatch("(a: int, /, b: str, *args, c: float, **kwargs)",
               "(a: int = 0, /, b: str = 'default', *args, c: float = 1.0, **kwargs)") // OK
    checkNotMatch("(a: int = 0, /, b: str = 'default', *args, c: float = 1.0, **kwargs)",
                  "(a: int, /, b: str, *args, c: float, **kwargs)") // a, b, c are missing default
  }

  fun testArgsKwargs() {
    checkMatch("(*args: int, **kwargs: str)", "(*args: int, **kwargs: str)")
    checkNotMatch("(*args: int, **kwargs: str)", "(*args: int, **kwargs: int)")
    checkNotMatch("(*args: str, **kwargs: str)", "(*args: int, **kwargs: int)")
    checkMatch("(a: int, *args: int, **kwargs: str)", "(a: int, *args: int, **kwargs: str)")
    checkNotMatch("(a: int, *args: str, **kwargs: str)", "(a: str, *args: int, **kwargs: str)")
  }

  fun testArgsKwargsWithTuple() {
    checkMatch("(*args: *tuple[int, ...], **kwargs: str)", "(a: int = 1, b: int = 1, *args: *tuple[int, ...], **kwargs: str)")
    checkMatch("(*args: *tuple[int, str], **kwargs: str)", "(*args: *tuple[int, str], **kwargs: str)")
    checkNotMatch("(*args: *tuple[int, str], **kwargs: str)", "(*args: *tuple[int, str], **kwargs: int)")
    checkMatch("(*args: *tuple[int, str], **kwargs: int)", "(a: int = 1, b: str = '', **kwargs: int)")
    // positional arguments for `a` and `b` map to `*args`, while keyword arguments map to `**kwargs`
    checkNotMatch("(a: int, b: str, **kwargs: int)", "(*args: *tuple[int, str], **kwargs: int)")
    checkMatch("(a: int, b: str, /, **kwargs: int)", "(*args: *tuple[int, str], **kwargs: int)")
    checkMatch("(*args: *tuple[int, ...], **kwargs: str)", "(a: int = 1, b: int = 1, *args: *tuple[int, ...], **kwargs: str)")
  }

  fun testDefaultValueEdgeCases() {
    checkMatch("()", "(a: int = 0, b: str = 'default', c: float = 1.0)")
    checkMatch("(a: int)", "(a: int = 0, b: str = 'default', c: float = 1.0)")
    checkNotMatch("(a: int = 0, b: str = 'default', c: float = 1.0)", "(a: int, b: str)")
    checkMatch("(a: int, b: str, c: float)", "(a: int, b: str, c: float = 1.0)")
    checkNotMatch("(a: int, b: str, c: float = 1.0)", "(a: int, b: str, c: float)")
    checkMatch("(a: int, b: str, c: float)", "(a: int = 0, b: str, c: float)")
    checkNotMatch("(a: int = 0, b: str, c: float)", "(a: int, b: str, c: float)")
  }

  fun testDunderGetParamsConsideredPosOnly() {
    checkMatch("(__a)", "(b)")
    checkMatch("(__k, __v)", "(a, b)") // Names are not important here
    checkNotMatch("(a, __k)", "(a, b)") // Should fail as `(a, __k)` is not a valid signature
    checkNotMatch("(__a, b, __c)", "(a, b, c)") // Should fail as `(__a, b, __c)` is not a valid signature
    checkMatch("(__a: int, __b: str)", "(*args: int | str)")
  }

  fun testCallableWithTypeVarTupleVsRealFunc() {
    checkMatch("[int, str, *Ts, str]", "(a: int, b: str, c: int, d: int, e: float, x: str, y: str)")
    checkMatch("[int, str, *Ts, str]", "(a: int, b: str, c: int, d: int, e: float, x: str)")
    checkMatch("[int, str, *Ts, str]", "(a: int, b: str, c: int, d: int, x: str)")
    checkMatch("[int, str, *Ts, str]", "(a: int, b: str, c: int, x: str)")
    checkMatch("[int, str, *Ts, str]", "(a: int, b: str, x: str)")
    checkNotMatch("[int, str, *Ts, str]", "(a: int, b: str, x: int)")
    checkNotMatch("[int, str, *Ts, str]", "(a: bool, b: str, x: str)")
    checkNotMatch("[str, str, *Ts, str]", "(a: int, b: str, x: str)")
    checkNotMatch("[int, str, *Ts, str]", "(a: int, b: str)")
    checkNotMatch("[int, str, *Ts, str]", "(a: int)")
    checkMatch("[int, str, *Ts]", "(a: int, b: str)")
    checkMatch("[int, str, *Ts]", "(a: int, b: str, /)")
    checkMatch("[*Ts]", "(a: int)")
    checkMatch("[*Ts]", "()")
    checkMatch("[*Ts]", "(a: int, b: str)")
    checkMatch("[*Ts]", "(a: int, b: str, c: float)")
    checkMatch("[*Ts]", "(a: int, b: str, c: float, /)")
  }

  fun testCallableWithTypeVarTupleVsCallableWithTypeVarTuple() {
    checkMatch("[int, str, *Ts, str]", "[int, str, *Ts, str]")
    checkMatch("[int, str, *Ts, str]", "[int, str, *Ts, bool, str]")
    checkMatch("[int, str, *Ts, str]", "[int, str, *Ts, bool, int, str]")
    checkNotMatch("[int, str, *Ts, str]", "[int, str, *Ts]")
    checkNotMatch("[int, str, *Ts, str]", "[int, *Ts]")
    checkNotMatch("[int, str, *Ts, str]", "[*Ts]")
  }

  fun testRealFuncVsTypingCallable() {
    checkMatch("()", "[]")
    checkMatch("(a: int, b: str, /)", "[int, str]")
    checkNotMatch("(a: int, b: str)", "[int, str]")
    checkMatch("(a: int, b: str, c: bool, /)", "[int, str, bool]")
    checkNotMatch("(a: int, b: str, c: bool)", "[int, str, bool]")
    checkNotMatch("(a: int, b: str, c: bool, /)", "[int, str, bool, float]")
    checkNotMatch("(a: int, b: str, c: bool = True, /)", "[int, str]")
    checkNotMatch("(a: int, b: str, c: bool = True, /)", "[int, str, bool]")
  }

  fun testTypingCallableVsTypingCallable() {
    checkMatch("[]", "[]")

    checkMatch("[int, str]", "[int, str]")
    checkMatch("[int, str, bool]", "[int, str, bool]")
    checkNotMatch("[int, str]", "[int]")
    checkNotMatch("[int]", "[int, str]")
    checkNotMatch("[int, str, bool]", "[int, str]")

    // Contravariance
    checkMatch("[int]", "[float]")
    checkNotMatch("[float]", "[int]")
    checkMatch("[int, str]", "[float, str]")
    checkNotMatch("[float, str]", "[int, str]")

    checkMatch("[int]", "[int | str]")
    checkNotMatch("[int | str]", "[int]")
    checkMatch("[int, str]", "[int | float, str]")
  }

  fun testTypingCallableVsRealFunc() {
    checkMatch("[]", "()")
    checkMatch("[]", "(a: int = 0)")
    checkMatch("[int, str]", "(a: int, b: str, /)")
    checkMatch("[int, str]", "(a: int, b: str)")
    checkMatch("[int, str, bool]", "(a: int, b: str, c: bool, /)")
    checkMatch("[int]", "(a: float, /)")
    checkNotMatch("[float]", "(a: int, /)")
    checkMatch("[int, str]", "(a: int, b: str = 'default', /)")
    checkMatch("[int]", "(a: int = 0, /)")
  }

  fun testPositionalOrKeywordVsWildcardSignature() {
    checkMatch("(a)", "(*args, **kwargs)")
    checkMatch("(a, b)", "(a, *args, **kwargs)")
    // positional-or-keyword "a" is "split" in an illegal way, it maps to a positional-only parameter when passed as a positional argument
    // or to the keyword-vararg "**kwargs" when passed with a name
    checkNotMatch("(a)", "(a, /, *args, **kwargs)")
    checkNotMatch("(a)", "(b, /, *args, **kwargs)")
    checkNotMatch("(a, b)", "(b, /, *args, **kwargs)")
    checkNotMatch("(a, *args)", "(a=1, /, *args, **kwargs)")
  }

  fun testKeywordOnlyVsAnotherOptionalKeywordOnlyAndKwargs() {
    checkMatch("(*, a)", "(*, b=None, **kwargs)")
  }

  fun checkNotMatch(expectedSignature: String, actualSignature: String) {
    checkMatch(expectedSignature, actualSignature, false)
  }

  fun checkMatch(expectedSignature: String, actualSignature: String, shouldMatch: Boolean = true) {
    fun String.isTypingCallable() =
      if (this.firstOrNull() == '[') true
      else if (this.firstOrNull() == '(') false
      else error("Invalid signature: $this")

    val expectedIsTypingCallable = expectedSignature.isTypingCallable()
    val actualIsTypingCallable = actualSignature.isTypingCallable()

    val expectedSubstitution = if (expectedIsTypingCallable)
      "my_callable1: Callable[$expectedSignature, None]"
    else
      "def my_callable1$expectedSignature: ..."

    val actualSubstitution = if (actualIsTypingCallable)
      "my_callable2: Callable[$actualSignature, None]"
    else
      "def my_callable2$actualSignature: ..."

    val fileText = """
      from typing import Callable, TypeVarTuple
      Ts = TypeVarTuple('Ts')
      $expectedSubstitution
      $actualSubstitution
    """.trimIndent()

    myFixture.configureByText(PythonFileType.INSTANCE, fileText)

    val expected = myFixture.findElementByText("my_callable1",
                                               if (expectedIsTypingCallable)
                                                 PyTargetExpression::class.java
                                               else PyFunction::class.java)
    assertNotNull(expected)
    val actual = myFixture.findElementByText("my_callable2",
                                             if (actualIsTypingCallable)
                                               PyTargetExpression::class.java
                                             else PyFunction::class.java)
    assertNotNull(actual)
    val context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile())
    val expectedCallable = context.getType(expected) as? PyCallableType
    assertNotNull(expectedCallable)
    val actualCallable = context.getType(actual) as? PyCallableType
    assertNotNull(actualCallable)
    val match = PyTypeChecker.match(expectedCallable!!, actualCallable!!, context)
    assertEquals(shouldMatch, match)
  }
}
