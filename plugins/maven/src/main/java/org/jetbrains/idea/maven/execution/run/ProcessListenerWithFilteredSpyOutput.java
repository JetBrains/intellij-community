// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenSimpleConsoleEventsBuffer;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

//this listener used for proper console output in buildtools - to hide or show spy log in process out, it is unrelated to build events processing
public class ProcessListenerWithFilteredSpyOutput implements ProcessListener {
  private final ProcessListener myListener;
  private final boolean myIsWindowsCmd;
  private final MavenSimpleConsoleEventsBuffer mySimpleConsoleEventsBuffer;
  private final ProcessHandler myProcessHandler;
  private final AtomicBoolean myTerminateEventReceived = new AtomicBoolean(false);


  ProcessListenerWithFilteredSpyOutput(ProcessListener listener,
                                       ProcessHandler processHandler,
                                       boolean withLoggingOutputStream,
                                       boolean isWindowsCmd) {
    myListener = listener;
    myProcessHandler = processHandler;
    myIsWindowsCmd = isWindowsCmd;
    mySimpleConsoleEventsBuffer = new MavenSimpleConsoleEventsBuffer.Builder(
      (l, k) -> myListener.onTextAvailable(new ProcessEvent(processHandler, l), k))
      .withSpyOutput(Registry.is("maven.spy.events.debug"))
      .withLoggingOutputStream(withLoggingOutputStream)
      .withHidingCmdExitQuestion(isWindowsCmd)
      .build();
  }

  @Override
  public void startNotified(@NotNull ProcessEvent event) {
    myListener.startNotified(event);
  }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) {
    myListener.processTerminated(event);
  }

  @Override
  public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
    myTerminateEventReceived.set(true);
    if (myIsWindowsCmd) {
      sayYes(2);
    }
    myListener.processWillTerminate(event, willBeDestroyed);
  }

  private void sayYes(int times) {
    for (int i = 0; i < times; i++) {
      try {
        OutputStream input = myProcessHandler.getProcessInput();
        if (input == null) {
          MavenLog.LOG.warn("Cannot say yes to exit because process unput is null");
          break;
        }
        input.write("y\r\n".getBytes(StandardCharsets.UTF_8));
      }
      catch (IOException e) {
        MavenLog.LOG.warn("exception while saying yes to exit:", e);
        break;
      }
    }
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    if (myTerminateEventReceived.get()) return;
    mySimpleConsoleEventsBuffer.addText(event.getText(), outputType);
  }
}
