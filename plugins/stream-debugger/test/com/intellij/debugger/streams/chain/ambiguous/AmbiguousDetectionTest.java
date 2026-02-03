// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.ambiguous;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class AmbiguousDetectionTest extends AmbiguousChainTestCase {
  public void testSimpleExpression() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testNestedExpression() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testSimpleFunctionParameter() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testNestedFunctionParameters() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testNestedFunctionParametersReversed() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testStreamProducerParameter() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testStreamIntermediateCallParameter() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testStreamTerminatorParameter() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testStreamAllPositions() {
    doTest(ResultChecker.chainsCountChecker(4));
  }

  public void testNestedStreamProducerParameter() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testNestedStreamIntermediateCallParameter() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testNestedStreamTerminatorCallParameter() {
    doTest(ResultChecker.chainsCountChecker(3));
  }

  public void testNestedCallInLambda() {
    doTest(ResultChecker.chainsCountChecker(2));
  }

  public void testNestedCallInAnonymous() {
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
