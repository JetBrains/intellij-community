// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.test.StreamChainBuilderFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.debugger.streams.test.StreamChainBuilderFixtureKt.streamChainBuilderFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class VariableProducedPositiveTest {
  private final TestFixture<StreamChainBuilderFixture> builderFixture = streamChainBuilderFixture("chain/positive/variable");

  @Test
  public void testWithIntermediate() {
    doTest();
  }

  @Test
  public void testTerminationOnly() {
    doTest();
  }

  private void doTest() {
    checkResultChains(builderFixture.get().buildChains());
  }

  private static void checkResultChains(@NotNull List<StreamChain> chains) {
    assertFalse(chains.isEmpty());
  }
}
