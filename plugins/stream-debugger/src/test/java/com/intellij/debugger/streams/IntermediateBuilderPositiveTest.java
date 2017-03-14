package com.intellij.debugger.streams;

import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

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
  protected void checkResultChain(StreamChain chain) {
    assertNotNull(chain);
    assertEquals(1, chain.getIntermediateCalls().size());
    final String callName = getTestName(true);
    final StreamCall call = chain.getIntermediateCalls().get(0);
    assertEquals(StreamCallType.INTERMEDIATE, call.getType());
    assertEquals(callName, call.getName());
    assertFalse(call.getArguments().isEmpty());
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "intermediate";
  }
}
