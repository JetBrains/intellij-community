package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class ProducerBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {
  public void testCollectionStream() throws Exception {
    doTest();
  }

  public void testCustomSource() throws Exception {
    doTest();
  }

  public void testIntStreamRange() throws Exception {
    doTest();
  }

  public void testIntStreamRangeClosed() throws Exception {
    doTest();
  }

  public void testIterate() throws Exception {
    doTest();
  }

  public void testConcat() throws Exception {
    doTest();
  }

  @Override
  protected void checkResultChain(StreamChain chain) {
    assertNotNull(chain);
    assertNotNull(chain.getProducerCall());
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "producer";
  }
}
