// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public void testLinkedChain() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "detection";
  }
}
