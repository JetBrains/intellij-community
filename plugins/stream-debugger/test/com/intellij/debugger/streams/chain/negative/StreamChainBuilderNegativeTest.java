// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.negative;

import com.intellij.debugger.streams.test.StreamChainBuilderTestCase;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class StreamChainBuilderNegativeTest extends StreamChainBuilderTestCase {

  public void testFakeStream() {
    doTest();
  }

  public void testWithoutTerminalOperation() {
    doTest();
  }

  public void testNoBreakpoint() {
    doTest();
  }

  public void testBreakpointOnMethod() {
    doTest();
  }

  public void testBreakpointOnIfCondition() {
    doTest();
  }

  public void testBreakpointOnNewScope() {
    doTest();
  }

  public void testBreakpointOnElseBranch() {
    doTest();
  }

  public void testInLambda() {
    doTest();
  }

  public void testInLambdaWithBody() {
    doTest();
  }

  public void testInAnonymous() {
    doTest();
  }

  public void testAfterStatement() {
    doTest();
  }

  public void testInPreviousStatement() {
    doTest();
  }

  public void testInNextStatement() {
    doTest();
  }

  public void testIdea173415() {
    doTest();
  }

  private void doTest() {
    final List<StreamChain> chains = buildChains();
    assertTrue(chains.isEmpty());
  }

  @Override
  protected List<StreamChain> buildChains() {
    return ApplicationManager.getApplication().runReadAction((Computable<List<StreamChain>>)() -> {
      final PsiElement elementAtCaret = configureAndGetElementAtCaret();
      assertNotNull(elementAtCaret);
      final StreamChainBuilder builder = getChainBuilder();
      assertFalse(builder.isChainExists(elementAtCaret));
      return builder.build(elementAtCaret);
    });
  }

  @NotNull
  @Override
  protected String getRelativeTestPath() {
    return "chain/negative";
  }
}
