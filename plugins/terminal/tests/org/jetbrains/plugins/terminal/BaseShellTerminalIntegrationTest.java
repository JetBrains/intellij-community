// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalModelListener;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public abstract class BaseShellTerminalIntegrationTest extends BasePlatformTestCase {

  public ShellTerminalWidget myWidget;
  public TerminalBufferWatcher myWatcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    LocalTerminalDirectRunner runner = LocalTerminalDirectRunner.createTerminalRunner(getProject());
    PtyProcess process = runner.createProcess(getProject().getBasePath());
    TtyConnector connector = new PtyProcessTtyConnector(process, StandardCharsets.UTF_8);
    myWidget = new ShellTerminalWidget(getProject(), new JBTerminalSystemSettingsProvider(), getTestRootDisposable());
    myWidget.start(connector);
    myWatcher = new TerminalBufferWatcher(myWidget.getTerminalTextBuffer(), myWidget.getTerminal());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myWidget.close();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public static class TerminalBufferWatcher {
    private final TerminalTextBuffer myBuffer;
    private final Terminal myTerminal;

    TerminalBufferWatcher(@NotNull TerminalTextBuffer buffer, @NotNull Terminal terminal) {
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
        Assert.assertEquals(expectedScreenLines, getScreenLines(true));
        Assert.fail("Unexpected failure");
      }
    }

    public void awaitScreenLinesEndWith(@NotNull List<String> expectedScreenLines, long timeoutMillis) {
      boolean ok = awaitBuffer(() -> checkScreenLinesEndWith(expectedScreenLines), timeoutMillis);
      if (!ok) {
        Assert.assertEquals(expectedScreenLines, getScreenLines(true));
        Assert.fail("Unexpected failure");
      }
    }

    public boolean awaitBuffer(@NotNull BooleanSupplier awaitCondition, long timeoutMillis) {
      if (awaitCondition.getAsBoolean()) return true;
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
      try {
        latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException e) {
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
  }
}
