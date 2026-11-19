// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.test.StreamChainBuilderFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.debugger.streams.test.StreamChainBuilderFixtureKt.streamChainBuilderFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class LocationBuilderPositiveTest {
  private final TestFixture<StreamChainBuilderFixture> builderFixture = streamChainBuilderFixture("chain/positive/location");

  @Test
  public void testAnonymousBody() {
    doTest();
  }

  @Test
  public void testAssignExpression() {
    doTest();
  }

  @Test
  public void testFirstParameterOfFunction() {
    doTest();
  }

  @Test
  public void testLambdaBody() {
    doTest();
  }

  @Test
  public void testParameterInAssignExpression() {
    doTest();
  }

  @Test
  public void testParameterInReturnExpression() {
    doTest();
  }

  @Test
  public void testReturnExpression() {
    doTest();
  }

  @Test
  public void testSecondParameterOfFunction() {
    doTest();
  }

  @Test
  public void testSingleExpression() {
    doTest();
  }

  @Test
  public void testBeforeStatement() {
    doTest();
  }

  @Test
  public void testAfterStatement() {
    doTest();
  }

  @Test
  public void testBetweenChainCallsBeforeDot() {
    doTest();
  }

  @Test
  public void testBetweenChainCallsAfterDot() {
    doTest();
  }

  @Test
  public void testInEmptyParameterList() {
    doTest();
  }

  @Test
  public void testBetweenParametersBeforeComma() {
    doTest();
  }

  @Test
  public void testBetweenParametersAfterComma() {
    doTest();
  }

  @Test
  public void testInAnyLambda() {
    doTest();
  }

  @Test
  public void testInAnyAnonymous() {
    doTest();
  }

  @Test
  public void testInString() {
    doTest();
  }

  @Test
  public void testInVariableName() {
    doTest();
  }

  @Test
  public void testInMethodReference() {
    doTest();
  }

  private void doTest() {
    checkResultChains(builderFixture.get().buildChains());
  }

  private static void checkResultChains(@NotNull List<StreamChain> chains) {
    assertFalse(chains.isEmpty());
  }
}
