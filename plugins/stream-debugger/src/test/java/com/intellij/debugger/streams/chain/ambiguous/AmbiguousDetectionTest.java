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
package com.intellij.debugger.streams.chain.ambiguous;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class AmbiguousDetectionTest extends AmbiguousChainTestCase {
  public void testSimpleExpression() throws Exception {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testNestedExpression() throws Exception {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testSimpleFunctionParameter() throws Exception {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testNestedFunctionParameters() throws Exception {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testNestedFunctionParametersReversed() throws Exception {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testStreamProducerParameter() throws Exception {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testStreamIntermediateCallParameter() throws Exception {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testStreamTerminatorParameter() throws Exception {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testStreamAllPositions() {
    doTest(ResultChecker.chainsCountChecker(4));
  }

  public void testNestedStreamProducerParameter() throws Exception {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testNestedStreamIntermediateCallParameter() throws Exception {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testNestedStreamTerminatorCallParameter() throws Exception {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testNestedCallInLambda() throws Exception {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testNestedCallInAnonymous() throws Exception {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "detection";
  }
}
