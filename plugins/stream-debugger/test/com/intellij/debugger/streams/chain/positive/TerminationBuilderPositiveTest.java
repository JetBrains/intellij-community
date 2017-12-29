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
public class TerminationBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {

  public void testAllMatch() {
    doTest();
  }

  public void testAnyMatch() {
    doTest();
  }

  public void testNoneMatch() {
    doTest();
  }

  public void testAverage() {
    doTest();
  }

  public void testClose() {
    doTest();
  }

  public void testCollect() {
    doTest();
  }

  public void testCount() {
    doTest();
  }

  public void testFindAny() {
    doTest();
  }

  public void testFindFirst() {
    doTest();
  }

  public void testForEach() {
    doTest();
  }

  public void testForEachOrdered() {
    doTest();
  }

  public void testIterator() {
    doTest();
  }

  public void testMax() {
    doTest();
  }

  public void testMin() {
    doTest();
  }

  public void testReduce() {
    doTest();
  }

  public void testSpliterator() {
    doTest();
  }

  public void testSummaryStatistics() {
    doTest();
  }

  public void testToArray() {
    doTest();
  }

  public void testSum() {
    doTest();
  }

  @Override
  protected void checkResultChains(@NotNull List<StreamChain> chains) {
    assertFalse(chains.isEmpty());
    final StreamChain chain = chains.get(0);
    final StreamCall terminationCall = chain.getTerminationCall();
    assertNotNull(terminationCall);

    final String expectedCallName = getTestName(true);
    assertEquals(expectedCallName, terminationCall.getName());
    assertEquals(StreamCallType.TERMINATOR, terminationCall.getType());
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "termination";
  }
}
