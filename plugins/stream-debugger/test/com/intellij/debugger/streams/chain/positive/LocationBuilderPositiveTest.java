// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class LocationBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {
  public void testAnonymousBody() {
    doTest();
  }

  public void testAssignExpression() {
    doTest();
  }

  public void testFirstParameterOfFunction() {
    doTest();
  }

  public void testLambdaBody() {
    doTest();
  }

  public void testParameterInAssignExpression() {
    doTest();
  }

  public void testParameterInReturnExpression() {
    doTest();
  }

  public void testReturnExpression() {
    doTest();
  }

  public void testSecondParameterOfFunction() {
    doTest();
  }

  public void testSingleExpression() {
    doTest();
  }

  public void testBeforeStatement() {
    doTest();
  }

  public void testAfterStatement() {
    doTest();
  }

  public void testBetweenChainCallsBeforeDot() {
    doTest();
  }

  public void testBetweenChainCallsAfterDot() {
    doTest();
  }

  public void testInEmptyParameterList() {
    doTest();
  }

  public void testBetweenParametersBeforeComma() {
    doTest();
  }

  public void testBetweenParametersAfterComma() {
    doTest();
  }

  public void testInAnyLambda() {
    doTest();
  }

  public void testInAnyAnonymous() {
    doTest();
  }

  public void testInString() {
    doTest();
  }

  public void testInVariableName() {
    doTest();
  }

  public void testInMethodReference() {
    doTest();
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "location";
  }

  @Override
  protected void checkResultChains(@NotNull List<StreamChain> chains) {
    assertFalse(chains.isEmpty());
  }
}
