// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.classic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalModelListener;
import com.jediterm.terminal.model.TerminalTextBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

class ClassicTerminalTestBufferWatcher {
  private static final Logger LOG = Logger.getInstance(ClassicTerminalTestBufferWatcher.class);
  private final TerminalTextBuffer myBuffer;
  private final Terminal myTerminal;

  ClassicTerminalTestBufferWatcher(@NotNull TerminalTextBuffer buffer, @NotNull Terminal terminal) {
    myBuffer = buffer;
    myTerminal = terminal;
  }

  @SuppressWarnings("SameParameterValue")
  private @NotNull List<String> getScreenLines(boolean aboveCursorLine) {
    List<String> screenLines = new ArrayList<>();
    myBuffer.lock();
    try {
      int cursorLineInd = myTerminal.getCursorY() - 1;
      for (int row = 0; row < myBuffer.getHeight(); row++) {
        if (!aboveCursorLine || row < cursorLineInd) {
          TerminalLine line = myBuffer.getLine(row);
          screenLines.add(line.getText());
        }
      }
    }
    finally {
      myBuffer.unlock();
    }
    return screenLines;
  }

  public void awaitScreenLinesAre(@NotNull List<String> expectedScreenLines, long timeoutMillis) {
    boolean ok = awaitBuffer(() -> expectedScreenLines.equals(getScreenLines(true)), timeoutMillis);
    if (!ok) {
      assertScreenLines(expectedScreenLines);
      throw new AssertionError("Unexpected failure");
    }
  }

  public void awaitScreenLinesEndWith(@NotNull List<String> expectedScreenLines, long timeoutMillis) {
    boolean ok = awaitBuffer(() -> checkScreenLinesEndWith(expectedScreenLines), timeoutMillis);
    if (!ok) {
      assertScreenLines(expectedScreenLines);
      throw new AssertionError("Unexpected failure");
    }
  }

  private void assertScreenLines(@NotNull List<String> expectedScreenLines) {
    List<String> actualScreenLines = getScreenLines(true);
    if (!ContainerUtil.equalsIdentity(expectedScreenLines, actualScreenLines)) {
      throw new AssertionError("Expected:\n" + expectedScreenLines + "\nActual:\n" + actualScreenLines);
    }
  }

  public boolean awaitBuffer(@NotNull BooleanSupplier awaitCondition, long timeoutMillis) {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean ok = new AtomicBoolean(false);
    TerminalModelListener listener = new TerminalModelListener() {
      @Override
      public void modelChanged() {
        if (awaitCondition.getAsBoolean()) {
          ok.set(true);
          latch.countDown();
        }
      }
    };
    myBuffer.addModelListener(listener);
    if (awaitCondition.getAsBoolean()) {
      myBuffer.removeModelListener(listener);
      return true;
    }
    try {
      latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException e) {
      LOG.debug("Could not get response in " + timeoutMillis + "ms. Terminal screen lines are: " + getScreenLines());
      throw new AssertionError(e);
    }
    finally {
      myBuffer.removeModelListener(listener);
    }
    return ok.get();
  }

  public boolean checkScreenLinesEndWith(@NotNull List<String> expectedScreenLines) {
    List<String> actualLines = getScreenLines(true);
    if (actualLines.size() < expectedScreenLines.size()) return false;
    List<String> lastActualLines = actualLines.subList(actualLines.size() - expectedScreenLines.size(), actualLines.size());
    return expectedScreenLines.equals(lastActualLines);
  }

  public String getScreenLines() {
    return myBuffer.getScreenLines();
  }
}
