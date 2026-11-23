// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class ProducerBuilderPositiveTest {
  private final TestFixture<StreamChainBuilderFixture> builderFixture = streamChainBuilderFixture("chain/positive/producer");

  @Test
  public void testCollectionStream() {
    doTest();
  }

  @Test
  public void testCustomSource() {
    doTest();
  }

  @Test
  public void testIntStreamRange() {
    doTest();
  }

  @Test
  public void testIntStreamRangeClosed() {
    doTest();
  }

  @Test
  public void testIterate() {
    doTest();
  }

  @Test
  public void testConcat() {
    doTest();
  }

  private void doTest() {
    checkResultChains(builderFixture.get().buildChains());
  }

  private static void checkResultChains(@NotNull List<StreamChain> chains) {
    assertFalse(chains.isEmpty());
    assertNotNull(chains.getFirst().getQualifierExpression());
  }
}
