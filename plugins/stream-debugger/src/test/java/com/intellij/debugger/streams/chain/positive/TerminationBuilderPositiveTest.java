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
