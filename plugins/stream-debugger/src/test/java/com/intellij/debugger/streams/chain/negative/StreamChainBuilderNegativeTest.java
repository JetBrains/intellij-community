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

  private void doTest() {
    final List<StreamChain> chains = buildChains();
    assertTrue(chains.isEmpty());
  }

  @NotNull
  @Override
  protected String getRelativeTestPath() {
    return "chain/negative";
  }
}
