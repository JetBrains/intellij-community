// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.ambiguous;

import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.test.StreamChainBuilderFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.debugger.streams.test.StreamChainBuilderFixtureKt.streamChainBuilderFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class AmbiguousDetectionTest {
  private final TestFixture<StreamChainBuilderFixture> builderFixture = streamChainBuilderFixture("chain/ambiguous/detection");

  @Test
  public void testSimpleExpression() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  @Test
  public void testNestedExpression() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  @Test
  public void testSimpleFunctionParameter() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  @Test
  public void testNestedFunctionParameters() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  @Test
  public void testNestedFunctionParametersReversed() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  @Test
  public void testStreamProducerParameter() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  @Test
  public void testStreamIntermediateCallParameter() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  @Test
  public void testStreamTerminatorParameter() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  @Test
  public void testStreamAllPositions() {
    doTest(ResultChecker.chainsCountChecker(4));
  }

  @Test
  public void testNestedStreamProducerParameter() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  @Test
  public void testNestedStreamIntermediateCallParameter() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  @Test
  public void testNestedStreamTerminatorCallParameter() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  @Test
  public void testNestedCallInLambda() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  @Test
  public void testNestedCallInAnonymous() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  @Test
  public void testLinkedChain() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  private void doTest(@NotNull ResultChecker resultChecker) {
    final List<StreamChain> chains = builderFixture.get().buildChains();
    assertNotNull(chains);
    resultChecker.check(chains);
  }

  @FunctionalInterface
  private interface ResultChecker {
    void check(@NotNull List<StreamChain> chains);

    static ResultChecker chainsCountChecker(int count) {
      return chains -> assertEquals(count, chains.size());
    }
  }
}
