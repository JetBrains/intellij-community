// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
  protected void checkResultChains(@NotNull List<StreamChain> chains) {
    assertFalse(chains.isEmpty());
    assertNotNull(chains.get(0).getQualifierExpression());
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "producer";
  }
}
