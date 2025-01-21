// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenSimpleConsoleEventsBuffer;

public class ProcessListenerWithFilteredSpyOutput implements ProcessListener {
  private final ProcessListener myListener;
  private final MavenSimpleConsoleEventsBuffer mySimpleConsoleEventsBuffer;

  ProcessListenerWithFilteredSpyOutput(ProcessListener listener, ProcessHandler processHandler) {
    myListener = listener;
    mySimpleConsoleEventsBuffer = new MavenSimpleConsoleEventsBuffer(
      (l, k) -> myListener.onTextAvailable(new ProcessEvent(processHandler, l), k),
      Registry.is("maven.spy.events.debug")
    );
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
    myListener.processWillTerminate(event, willBeDestroyed);
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    mySimpleConsoleEventsBuffer.addText(event.getText(), outputType);
  }
}
