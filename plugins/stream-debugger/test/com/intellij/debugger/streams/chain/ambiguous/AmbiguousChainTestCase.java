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
package com.intellij.debugger.streams.chain.ambiguous;

import com.intellij.debugger.streams.chain.StreamChainBuilderTestCase;
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
