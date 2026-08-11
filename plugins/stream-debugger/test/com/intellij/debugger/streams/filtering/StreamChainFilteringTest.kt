// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.filtering

internal class StreamChainFilteringTest : StreamChainFilteringTestCase() {
  fun testSingleChain() = doFilteringTest(
    "SingleChain",
    BeforeInvoke("of"),
    AfterInvoke("of"),
    BeforeInvoke("map"),
    AfterInvoke("map"),
    BeforeInvoke("filter"),
    AfterInvoke("filter"),
    BeforeInvoke("sum"),
  )

  fun testNoIntermediate() = doFilteringTest(
    "NoIntermediate",
    BeforeInvoke("of"),
    AfterInvoke("of"),
    BeforeInvoke("count"),
    AfterInvoke("count"),
  )

  fun testManyIntermediates() = doFilteringTest(
    "ManyIntermediates",
    BeforeInvoke("of"),
    BeforeInvoke("map", occurrence = 0),
    AfterInvoke("map", occurrence = 0),
    BeforeInvoke("filter"),
    BeforeInvoke("map", occurrence = 1),
    AfterInvoke("map", occurrence = 1),
    BeforeInvoke("sum"),
  )

  fun testTwoIndependentChains() = doFilteringTest(
    "TwoIndependentChains",
    BeforeInvoke("of", occurrence = 0),
    AfterInvoke("of", occurrence = 0),
    BeforeInvoke("sum", occurrence = 0),
    AfterInvoke("sum", occurrence = 0),
    BeforeInvoke("of", occurrence = 1),
    BeforeInvoke("filter"),
    AfterInvoke("filter"),
    BeforeInvoke("sum", occurrence = 1),
  )

  fun testNestedInIntermediateArgument() = doFilteringTest(
    "NestedInIntermediateArgument",
    BeforeInvoke("of", occurrence = 0),
    AfterInvoke("of", occurrence = 0),
    BeforeInvoke("of", occurrence = 1),
    BeforeInvoke("count", occurrence = 0),
    AfterInvoke("count", occurrence = 0),
    BeforeInvoke("limit"),
    BeforeInvoke("count", occurrence = 1),
  )

  // Linked chains: the result of the first is the qualifier of the second (`...toList().stream()...`).
  fun testQualifierChain() = doFilteringTest(
    "QualifierChain",
    BeforeInvoke("of"),
    BeforeInvoke("map"),
    AfterInvoke("map"),
    BeforeInvoke("toList", occurrence = 0),
    AfterInvoke("toList", occurrence = 0),
    BeforeInvoke("stream"),
    BeforeInvoke("filter"),
    BeforeInvoke("toList", occurrence = 1),
  )

  fun testMultiLineChain() = doFilteringTest(
    "MultiLineChain",
    BeforeInvoke("of"),
    AfterInvoke("of"),
    BeforeInvoke("map"),
    AfterInvoke("map"),
    BeforeInvoke("filter"),
    BeforeInvoke("sum"),
  )

  fun testStreamInsideLambda() = doFilteringTestAtBreakpoint("StreamInsideLambda")

  fun testStopInsideQualifier() = doFilteringTest(
    "StopInsideQualifier",
    BeforeInvoke("makeArray"),
    AfterInvoke("makeArray"),
    BeforeInvoke("stream"),
    AfterInvoke("stream"),
    BeforeInvoke("map"),
    BeforeInvoke("sum"),
  )

  // Positions during argument evaluation between calls: `combine(IntStream.of(...).sum(), IntStream.of(...).sum())`.
  fun testArgEvalBetweenCalls() = doFilteringTest(
    "ArgEvalBetweenCalls",
    BeforeInvoke("of", occurrence = 0),
    AfterInvoke("sum", occurrence = 0),
    BeforeInvoke("of", occurrence = 1),
    AfterInvoke("sum", occurrence = 1),
    BeforeInvoke("combine"),
  )

  fun testNestedInProducerArgument() = doFilteringTest(
    "NestedInProducerArgument",
    BeforeInvoke("of", occurrence = 0),
    AfterInvoke("toList", occurrence = 0),
    BeforeInvoke("of", occurrence = 1),
    BeforeInvoke("map"),
    AfterInvoke("toList", occurrence = 1),
    BeforeInvoke("of", occurrence = 2),
    BeforeInvoke("flatMap"),
    BeforeInvoke("count"),
  )

  // Two statements on one line
  // (future extension: detection currently only sees the first statement).
  fun testTwoStatementsOneLine() = doFilteringTest(
    "TwoStatementsOneLine",
    BeforeInvoke("of", occurrence = 0),
    AfterInvoke("toList", occurrence = 0),
    BeforeInvoke("of", occurrence = 1),
    AfterInvoke("toList", occurrence = 1),
  )

  fun testTwoIdenticalChainsSameLine() = doFilteringTest(
    "TwoIdenticalChainsSameLine",
    BeforeInvoke("stream", occurrence = 0),
    AfterInvoke("toList", occurrence = 0),
    BeforeInvoke("stream", occurrence = 1),
    AfterInvoke("toList", occurrence = 1),
  )

  fun testTwoIdenticalChainsMultiLine() = doFilteringTest(
    "TwoIdenticalChainsMultiLine",
    BeforeInvoke("stream", occurrence = 0),
    AfterInvoke("toList", occurrence = 0),
    BeforeInvoke("stream", occurrence = 1),
    AfterInvoke("toList", occurrence = 1),
  )

  fun testDeeplyNestedStreams() = doFilteringTest(
    "DeeplyNestedStreams",
    BeforeInvoke("stream"),
    BeforeInvoke("count", occurrence = 0),
    BeforeInvoke("count", occurrence = 1),
    BeforeInvoke("limit", occurrence = 0),
    BeforeInvoke("map"),
    BeforeInvoke("filter"),
    BeforeInvoke("toList", occurrence = 0),
    BeforeInvoke("limit", occurrence = 1),
    BeforeInvoke("toList", occurrence = 1),
  )

  fun testStopInsideBlockLambda() = doFilteringTestAtBreakpoint("StopInsideBlockLambda")

  // Stop inside a lambda of a chain that is itself an argument of another chain: the inner chain is already running,
  // and the outer one is not traceable from the frame of that lambda even it has not reached `limit` yet.
  fun testStopInsideNestedArgumentLambda() = doFilteringTestAtBreakpoint("StopInsideNestedArgumentLambda")

  // A chain in a field initializer of an anonymous class. We stop in the synthetic `<init>` of the anonymous class,
  // and `DebuggerUtilsEx.getContainingMethod` returns the lexically enclosing `main` as the host,
  // and the traversal of its body never enters class bodies.
  // Nothing is matched against the bytecode, and every chain stays traceable.
  fun testFieldInitializerInAnonymousClass() = doFilteringTest(
    "FieldInitializerInAnonymousClass",
    BeforeInvoke("of"),
    AfterInvoke("of"),
    BeforeInvoke("map"),
    AfterInvoke("map"),
    BeforeInvoke("count"),
  )

  // Several independent streams in one multi-line statement: the fast path alone (line comparison) already
  // filters the streams that are fully above the stop line, without any bytecode analysis.
  fun testMultiLineStatement() = doFilteringTest(
    "MultiLineStatement",
    BeforeInvoke("sum", occurrence = 0),
    BeforeInvoke("sum", occurrence = 1),
    BeforeInvoke("sum", occurrence = 2),
  )
}
