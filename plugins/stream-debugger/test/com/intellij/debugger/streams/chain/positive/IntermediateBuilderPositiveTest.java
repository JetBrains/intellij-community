// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.core.wrapper.StreamCall;
import com.intellij.debugger.streams.core.wrapper.StreamCallType;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.test.StreamChainBuilderFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.debugger.streams.test.StreamChainBuilderFixtureKt.streamChainBuilderFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class IntermediateBuilderPositiveTest {
  private final TestFixture<StreamChainBuilderFixture> builderFixture = streamChainBuilderFixture("chain/positive/intermediate");

  @Test
  public void testFilter() {
    doTest();
  }

  @Test
  public void testMap() {
    doTest();
  }

  @Test
  public void testMapToInt() {
    doTest();
  }

  @Test
  public void testMapToDouble() {
    doTest();
  }

  @Test
  public void testMapToLong() {
    doTest();
  }

  @Test
  public void testFlatMap() {
    doTest();
  }

  @Test
  public void testFlatMapToInt() {
    doTest();
  }

  @Test
  public void testFlatMapToLong() {
    doTest();
  }

  @Test
  public void testFlatMapToDouble() {
    doTest();
  }

  @Test
  public void testDistinct() {
    doTest();
  }

  @Test
  public void testSorted() {
    doTest();
  }

  @Test
  public void testLimit() {
    doTest();
  }

  @Test
  public void testBoxed() {
    doTest();
  }

  @Test
  public void testPeek() {
    doTest();
  }

  @Test
  public void testOnClose() {
    doTest();
  }

  private void doTest() {
    checkResultChains(builderFixture.get().buildChains());
  }

  private void checkResultChains(@NotNull List<StreamChain> chains) {
    assertFalse(chains.isEmpty());
    final StreamChain chain = chains.getFirst();
    assertEquals(1, chain.getIntermediateCalls().size());
    final String callName = builderFixture.get().getTestName(true);
    final StreamCall call = chain.getIntermediateCalls().getFirst();
    assertEquals(StreamCallType.INTERMEDIATE, call.getType());
    assertEquals(callName, call.getName());
  }
}
