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

import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class IntermediateBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {
  public void testFilter() throws Exception {
    doTest();
  }

  public void testMap() throws Exception {
    doTest();
  }

  public void testMapToInt() throws Exception {
    doTest();
  }

  public void testMapToDouble() throws Exception {
    doTest();
  }

  public void testMapToLong() throws Exception {
    doTest();
  }

  public void testFlatMap() throws Exception {
    doTest();
  }

  public void testFlatMapToInt() throws Exception {
    doTest();
  }

  public void testFlatMapToLong() throws Exception {
    doTest();
  }

  public void testFlatMapToDouble() throws Exception {
    doTest();
  }

  public void testDistinct() throws Exception {
    doTest();
  }

  public void testSorted() throws Exception {
    doTest();
  }

  public void testLimit() throws Exception {
    doTest();
  }

  public void testBoxed() throws Exception {
    doTest();
  }

  public void testPeek() throws Exception {
    doTest();
  }

  public void testOnClose() throws Exception {
    doTest();
  }

  @Override
  protected void checkResultChains(@NotNull List<StreamChain> chains) {
    assertFalse(chains.isEmpty());
    final StreamChain chain = chains.get(0);
    assertEquals(1, chain.getIntermediateCalls().size());
    final String callName = getTestName(true);
    final StreamCall call = chain.getIntermediateCalls().get(0);
    assertEquals(StreamCallType.INTERMEDIATE, call.getType());
    assertEquals(callName, call.getName());
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "intermediate";
  }
}
