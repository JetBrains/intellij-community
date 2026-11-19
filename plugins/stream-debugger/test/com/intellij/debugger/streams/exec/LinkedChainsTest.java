// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.core.testFramework.ChainSelector;
import com.intellij.debugger.streams.core.testFramework.TraceExecutionTestHelper;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.test.ExecutionTestCaseHelper;
import com.intellij.debugger.streams.test.TraceExecutionFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.intellij.debugger.streams.test.TraceExecutionFixtureKt.traceExecutionFixture;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class LinkedChainsTest {
  protected final TestFixture<TraceExecutionFixture> traceFixture = traceExecutionFixture(this::getHelper);

  @Test
  public void testLinkedFirstChain() {
    doTest(0);
  }

  @Test
  public void testLinkedSecondChain() {
    doTest(1);
  }

  @Test
  public void testLinkedThirdChain() {
    doTest(2);
  }

  @Test
  public void testLinkedFourChain() {
    doTest(3);
  }

  private void doTest(int index) {
    traceFixture.get().doTest(false, "LinkedChains", new LengthChainSelector(index));
  }

  private static class LengthChainSelector implements ChainSelector {
    private final int myIndex;

    LengthChainSelector(int index) {
      myIndex = index;
    }

    @NotNull
    @Override
    public StreamChain select(@NotNull List<? extends StreamChain> chains) {
      final List<StreamChain> orderedChains = new ArrayList<>(chains);
      orderedChains.sort(Comparator.comparingInt(x -> x.getText().length()));
      return orderedChains.get(myIndex);
    }
  }

  protected @NotNull TraceExecutionTestHelper getHelper(@NotNull TraceExecutionFixture trace, @NotNull XDebugSession session) {
    return new ExecutionTestCaseHelper(trace, session);
  }
}
