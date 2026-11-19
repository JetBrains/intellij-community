// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.negative;

import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.core.wrapper.StreamChainBuilder;
import com.intellij.debugger.streams.test.StreamChainBuilderFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.debugger.streams.test.StreamChainBuilderFixtureKt.streamChainBuilderFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class StreamChainBuilderNegativeTest {
  private final TestFixture<StreamChainBuilderFixture> builderFixture = streamChainBuilderFixture("chain/negative");

  @Test
  public void testFakeStream() {
    doTest();
  }

  @Test
  public void testWithoutTerminalOperation() {
    doTest();
  }

  @Test
  public void testNoBreakpoint() {
    doTest();
  }

  @Test
  public void testBreakpointOnMethod() {
    doTest();
  }

  @Test
  public void testBreakpointOnIfCondition() {
    doTest();
  }

  @Test
  public void testBreakpointOnNewScope() {
    doTest();
  }

  @Test
  public void testBreakpointOnElseBranch() {
    doTest();
  }

  @Test
  public void testInLambda() {
    doTest();
  }

  @Test
  public void testInLambdaWithBody() {
    doTest();
  }

  @Test
  public void testInAnonymous() {
    doTest();
  }

  @Test
  public void testAfterStatement() {
    doTest();
  }

  @Test
  public void testInPreviousStatement() {
    doTest();
  }

  @Test
  public void testInNextStatement() {
    doTest();
  }

  @Test
  public void testIdea173415() {
    doTest();
  }

  private void doTest() {
    final List<StreamChain> chains = buildChains();
    assertTrue(chains.isEmpty());
  }

  private List<StreamChain> buildChains() {
    final StreamChainBuilderFixture fixture = builderFixture.get();
    final PsiElement elementAtCaret = fixture.configureAndGetElementAtCaret();
    assertNotNull(elementAtCaret);
    return ReadAction.computeBlocking(() -> {
      final StreamChainBuilder builder = fixture.getChainBuilder();
      assertFalse(builder.isChainExists(elementAtCaret));
      return builder.build(elementAtCaret);
    });
  }
}
