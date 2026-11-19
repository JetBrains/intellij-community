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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class TerminationBuilderPositiveTest {
  private final TestFixture<StreamChainBuilderFixture> builderFixture = streamChainBuilderFixture("chain/positive/termination");

  @Test
  public void testAllMatch() {
    doTest();
  }

  @Test
  public void testAnyMatch() {
    doTest();
  }

  @Test
  public void testNoneMatch() {
    doTest();
  }

  @Test
  public void testAverage() {
    doTest();
  }

  @Test
  public void testClose() {
    doTest();
  }

  @Test
  public void testCollect() {
    doTest();
  }

  @Test
  public void testCount() {
    doTest();
  }

  @Test
  public void testFindAny() {
    doTest();
  }

  @Test
  public void testFindFirst() {
    doTest();
  }

  @Test
  public void testForEach() {
    doTest();
  }

  @Test
  public void testForEachOrdered() {
    doTest();
  }

  @Test
  public void testIterator() {
    doTest();
  }

  @Test
  public void testMax() {
    doTest();
  }

  @Test
  public void testMin() {
    doTest();
  }

  @Test
  public void testReduce() {
    doTest();
  }

  @Test
  public void testSpliterator() {
    doTest();
  }

  @Test
  public void testSummaryStatistics() {
    doTest();
  }

  @Test
  public void testToArray() {
    doTest();
  }

  @Test
  public void testSum() {
    doTest();
  }

  private void doTest() {
    checkResultChains(builderFixture.get().buildChains());
  }

  private void checkResultChains(@NotNull List<StreamChain> chains) {
    assertFalse(chains.isEmpty());
    final StreamChain chain = chains.getFirst();
    final StreamCall terminationCall = chain.getTerminationCall();
    assertNotNull(terminationCall);

    final String expectedCallName = builderFixture.get().getTestName(true);
    assertEquals(expectedCallName, terminationCall.getName());
    assertEquals(StreamCallType.TERMINATOR, terminationCall.getType());
  }
}
