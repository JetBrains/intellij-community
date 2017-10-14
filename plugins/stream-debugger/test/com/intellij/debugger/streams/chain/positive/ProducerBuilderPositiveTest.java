// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class ProducerBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {
  public void testCollectionStream() {
    doTest();
  }

  public void testCustomSource() {
    doTest();
  }

  public void testIntStreamRange() {
    doTest();
  }

  public void testIntStreamRangeClosed() {
    doTest();
  }

  public void testIterate() {
    doTest();
  }

  public void testConcat() {
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
