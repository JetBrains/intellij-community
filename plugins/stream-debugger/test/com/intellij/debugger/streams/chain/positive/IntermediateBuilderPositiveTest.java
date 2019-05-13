// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public void testFilter() {
    doTest();
  }

  public void testMap() {
    doTest();
  }

  public void testMapToInt() {
    doTest();
  }

  public void testMapToDouble() {
    doTest();
  }

  public void testMapToLong() {
    doTest();
  }

  public void testFlatMap() {
    doTest();
  }

  public void testFlatMapToInt() {
    doTest();
  }

  public void testFlatMapToLong() {
    doTest();
  }

  public void testFlatMapToDouble() {
    doTest();
  }

  public void testDistinct() {
    doTest();
  }

  public void testSorted() {
    doTest();
  }

  public void testLimit() {
    doTest();
  }

  public void testBoxed() {
    doTest();
  }

  public void testPeek() {
    doTest();
  }

  public void testOnClose() {
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
