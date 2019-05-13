// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.ambiguous;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class AmbiguousOrderTest extends AmbiguousChainTestCase {

  public void testEmpty() {

  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "order";
  }
}
