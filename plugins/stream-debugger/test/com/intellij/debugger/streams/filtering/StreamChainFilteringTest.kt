// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.filtering

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
internal class StreamChainFilteringTest {
  private val filtering by streamChainFilteringFixture()

  @Test
  fun testSingleChain(): Unit = filtering.doFilteringTest(
    "SingleChain",
    BeforeInvoke("of"),
    AfterInvoke("of"),
    BeforeInvoke("map"),
    AfterInvoke("map"),
    BeforeInvoke("filter"),
    AfterInvoke("filter"),
    BeforeInvoke("sum"),
  )

  @Test
  fun testNoIntermediate(): Unit = filtering.doFilteringTest(
    "NoIntermediate",
    BeforeInvoke("of"),
    AfterInvoke("of"),
    BeforeInvoke("count"),
    AfterInvoke("count"),
  )

  @Test
  fun testManyIntermediates(): Unit = filtering.doFilteringTest(
    "ManyIntermediates",
    BeforeInvoke("of"),
    BeforeInvoke("map", occurrence = 0),
    AfterInvoke("map", occurrence = 0),
    BeforeInvoke("filter"),
    BeforeInvoke("map", occurrence = 1),
    AfterInvoke("map", occurrence = 1),
    BeforeInvoke("sum"),
  )

  @Test
  fun testTwoIndependentChains(): Unit = filtering.doFilteringTest(
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

  @Test
  fun testNestedInIntermediateArgument(): Unit = filtering.doFilteringTest(
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
  @Test
  fun testQualifierChain(): Unit = filtering.doFilteringTest(
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

  @Test
  fun testMultiLineChain(): Unit = filtering.doFilteringTest(
    "MultiLineChain",
    BeforeInvoke("of"),
    AfterInvoke("of"),
    BeforeInvoke("map"),
    AfterInvoke("map"),
    BeforeInvoke("filter"),
    BeforeInvoke("sum"),
  )

  @Test
  fun testStreamInsideLambda(): Unit = filtering.doFilteringTestAtBreakpoint("StreamInsideLambda")

  @Test
  fun testStopInsideQualifier(): Unit = filtering.doFilteringTest(
    "StopInsideQualifier",
    BeforeInvoke("makeArray"),
    AfterInvoke("makeArray"),
    BeforeInvoke("stream"),
    AfterInvoke("stream"),
    BeforeInvoke("map"),
    BeforeInvoke("sum"),
  )

  // Positions during argument evaluation between calls: `combine(IntStream.of(...).sum(), IntStream.of(...).sum())`.
  @Test
  fun testArgEvalBetweenCalls(): Unit = filtering.doFilteringTest(
    "ArgEvalBetweenCalls",
    BeforeInvoke("of", occurrence = 0),
    AfterInvoke("sum", occurrence = 0),
    BeforeInvoke("of", occurrence = 1),
    AfterInvoke("sum", occurrence = 1),
    BeforeInvoke("combine"),
  )

  @Test
  fun testNestedInProducerArgument(): Unit = filtering.doFilteringTest(
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
  @Test
  fun testTwoStatementsOneLine(): Unit = filtering.doFilteringTest(
    "TwoStatementsOneLine",
    BeforeInvoke("of", occurrence = 0),
    AfterInvoke("toList", occurrence = 0),
    BeforeInvoke("of", occurrence = 1),
    AfterInvoke("toList", occurrence = 1),
  )

  @Test
  fun testTwoIdenticalChainsSameLine(): Unit = filtering.doFilteringTest(
    "TwoIdenticalChainsSameLine",
    BeforeInvoke("stream", occurrence = 0),
    AfterInvoke("toList", occurrence = 0),
    BeforeInvoke("stream", occurrence = 1),
    AfterInvoke("toList", occurrence = 1),
  )

  @Test
  fun testTwoIdenticalChainsMultiLine(): Unit = filtering.doFilteringTest(
    "TwoIdenticalChainsMultiLine",
    BeforeInvoke("stream", occurrence = 0),
    AfterInvoke("toList", occurrence = 0),
    BeforeInvoke("stream", occurrence = 1),
    AfterInvoke("toList", occurrence = 1),
  )

  @Test
  fun testDeeplyNestedStreams(): Unit = filtering.doFilteringTest(
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

  @Test
  fun testStopInsideBlockLambda(): Unit = filtering.doFilteringTestAtBreakpoint("StopInsideBlockLambda")

  // Stop inside a lambda of a chain that is itself an argument of another chain: the inner chain is already running,
  // and the outer one is not traceable from the frame of that lambda even it has not reached `limit` yet.
  @Test
  fun testStopInsideNestedArgumentLambda(): Unit = filtering.doFilteringTestAtBreakpoint("StopInsideNestedArgumentLambda")

  // A chain in a field initializer of an anonymous class. We stop in the synthetic `<init>` of the anonymous class,
  // and `DebuggerUtilsEx.getContainingMethod` returns the lexically enclosing `main` as the host,
  // and the traversal of its body never enters class bodies.
  // Nothing is matched against the bytecode, and every chain stays traceable.
  @Test
  fun testFieldInitializerInAnonymousClass(): Unit = filtering.doFilteringTest(
    "FieldInitializerInAnonymousClass",
    BeforeInvoke("of"),
    AfterInvoke("of"),
    BeforeInvoke("map"),
    AfterInvoke("map"),
    BeforeInvoke("count"),
  )

  // Several independent streams in one multi-line statement: the fast path alone (line comparison) already
  // filters the streams that are fully above the stop line, without any bytecode analysis.
  @Test
  fun testMultiLineStatement(): Unit = filtering.doFilteringTest(
    "MultiLineStatement",
    BeforeInvoke("sum", occurrence = 0),
    BeforeInvoke("sum", occurrence = 1),
    BeforeInvoke("sum", occurrence = 2),
  )
}
