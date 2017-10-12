// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class LocationBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {
  public void testAnonymousBody() throws Exception {
    doTest();
  }

  public void testAssignExpression() throws Exception {
    doTest();
  }

  public void testFirstParameterOfFunction() throws Exception {
    doTest();
  }

  public void testLambdaBody() throws Exception {
    doTest();
  }

  public void testParameterInAssignExpression() throws Exception {
    doTest();
  }

  public void testParameterInReturnExpression() throws Exception {
    doTest();
  }

  public void testReturnExpression() throws Exception {
    doTest();
  }

  public void testSecondParameterOfFunction() throws Exception {
    doTest();
  }

  public void testSingleExpression() throws Exception {
    doTest();
  }

  public void testBeforeStatement() throws Exception {
    doTest();
  }

  public void testAfterStatement() throws Exception {
    doTest();
  }

  public void testBetweenChainCallsBeforeDot() throws Exception {
    doTest();
  }

  public void testBetweenChainCallsAfterDot() throws Exception {
    doTest();
  }

  public void testInEmptyParameterList() throws Exception {
    doTest();
  }

  public void testBetweenParametersBeforeComma() throws Exception {
    doTest();
  }

  public void testBetweenParametersAfterComma() throws Exception {
    doTest();
  }

  public void testInAnyLambda() throws Exception {
    doTest();
  }

  public void testInAnyAnonymous() throws Exception {
    doTest();
  }

  public void testInString() throws Exception {
    doTest();
  }

  public void testInVariableName() throws Exception {
    doTest();
  }

  public void testInMethodReference() throws Exception {
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
