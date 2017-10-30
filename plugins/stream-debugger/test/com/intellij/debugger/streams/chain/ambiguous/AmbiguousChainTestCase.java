// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.ambiguous;

import com.intellij.debugger.streams.test.StreamChainBuilderTestCase;
import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class AmbiguousChainTestCase extends StreamChainBuilderTestCase {

  protected void doTest(@NotNull ResultChecker resultChecker) {
    final List<StreamChain> chains = buildChains();
    assertNotNull(chains);
    resultChecker.check(chains);
  }

  @NotNull
  @Override
  protected String getRelativeTestPath() {
    return "chain" + File.separator + "ambiguous" + File.separator + getDirectoryName();
  }

  @NotNull
  protected abstract String getDirectoryName();

  @FunctionalInterface
  public interface ResultChecker {
    void check(@NotNull List<StreamChain> chains);

    static ResultChecker chainsCountChecker(int count) {
      return chains -> assertEquals(count, chains.size());
    }
  }
}
