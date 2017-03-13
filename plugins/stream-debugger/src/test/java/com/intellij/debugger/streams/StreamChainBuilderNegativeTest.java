package com.intellij.debugger.streams;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class StreamChainBuilderNegativeTest extends StreamChainBuilderFixtureTestCase {
  @NotNull
  @Override
  protected String getRelativeTestPath() {
    return "chain/negative";
  }
}
