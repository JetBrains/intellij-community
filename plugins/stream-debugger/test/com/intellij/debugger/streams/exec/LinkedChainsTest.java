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
