/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
