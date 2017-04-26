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
package com.intellij.debugger.streams.chain.negative;

import com.intellij.debugger.streams.chain.StreamChainBuilderTestCase;
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
