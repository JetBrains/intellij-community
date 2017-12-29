// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.test.StreamChainBuilderTestCase;
import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class StreamChainBuilderPositiveTestBase extends StreamChainBuilderTestCase {

  @NotNull
  @Override
  protected String getRelativeTestPath() {
    return "chain" + File.separator + "positive" + File.separator + getDirectoryName();
  }

  void doTest() {
    checkResultChains(buildChains());
  }

  @NotNull
  protected abstract String getDirectoryName();

  protected void checkResultChains(@NotNull List<StreamChain> chains) {
  }
}
