// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class VariableProducedPositiveTest extends StreamChainBuilderPositiveTestBase {
  public void testWithIntermediate() {
    doTest();
  }

  public void testTerminationOnly() {
    doTest();
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "variable";
  }

  @Override
  protected void checkResultChains(@NotNull List<StreamChain> chains) {
    assertFalse(chains.isEmpty());
  }
}
