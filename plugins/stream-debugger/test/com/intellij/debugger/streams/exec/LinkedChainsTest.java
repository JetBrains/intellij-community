// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.test.TraceExecutionTestCase;
import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class LinkedChainsTest extends TraceExecutionTestCase {
  public void testLinkedFirstChain() {
    doTest(0);
  }

  public void testLinkedSecondChain() {
    doTest(1);
  }

  public void testLinkedThirdChain() {
    doTest(2);
  }

  public void testLinkedFourChain() {
    doTest(3);
  }

  private void doTest(int index) {
    doTest(false, "LinkedChains", new LengthChainSelector(index));
  }

  private static class LengthChainSelector implements ChainSelector {
    private final int myIndex;

    LengthChainSelector(int index) {
      myIndex = index;
    }

    @NotNull
    @Override
    public StreamChain select(@NotNull List<StreamChain> chains) {
      final List<StreamChain> orderedChains = new ArrayList<>(chains);
      orderedChains.sort(Comparator.comparingInt(x -> x.getText().length()));
      return orderedChains.get(myIndex);
    }
  }
}
