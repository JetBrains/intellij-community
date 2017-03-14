package com.intellij.debugger.streams;

import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class TerminationBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {

  public void testAllMatch() throws Exception {
    doTest();
  }

  public void testAnyMatch() throws Exception {
    doTest();
  }

  public void testNoneMatch() throws Exception {
    doTest();
  }

  public void testAverage() throws Exception {
    doTest();
  }

  public void testClose() throws Exception {
    doTest();
  }

  public void testCollect() throws Exception {
    doTest();
  }

  public void testCount() throws Exception {
    doTest();
  }

  public void testFindAny() throws Exception {
    doTest();
  }

  public void testFindFirst() throws Exception {
    doTest();
  }

  public void testForEach() throws Exception {
    doTest();
  }

  public void testForEachOrdered() throws Exception {
    doTest();
  }

  public void testIterator() throws Exception {
    doTest();
  }

  public void testMax() throws Exception {
    doTest();
  }

  public void testMin() throws Exception {
    doTest();
  }

  public void testReduce() throws Exception {
    doTest();
  }

  public void testSpliterator() throws Exception {
    doTest();
  }

  public void testSummaryStatistics() throws Exception {
    doTest();
  }

  public void testToArray() throws Exception {
    doTest();
  }

  public void testSum() throws Exception {
    doTest();
  }

  @Override
  protected void checkResultChain(StreamChain chain) {
    assertNotNull(chain);
    final StreamCall terminationCall = chain.getTerminationCall();
    assertNotNull(terminationCall);

    final String expectedCallName = getTestName(true);
    assertEquals(expectedCallName, terminationCall.getName());
    assertEquals(StreamCallType.TERMINATOR, terminationCall.getType());
    assertFalse(terminationCall.getArguments().isEmpty());
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "termination";
  }
}
