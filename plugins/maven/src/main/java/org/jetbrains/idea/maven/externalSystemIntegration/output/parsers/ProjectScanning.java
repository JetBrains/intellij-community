// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.FinishEventImpl;
import com.intellij.build.events.impl.StartEventImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;

import java.util.function.Consumer;

public class ProjectScanning implements MavenLoggedEventParser {
  private static final String NAME = "Scanning for projects";
  boolean inScan = false;
  boolean waitForComplete = false;
  private boolean completed;

  @Override
  public boolean supportsType(LogMessageType type) {
    return type == LogMessageType.INFO;
  }

  @Override
  public boolean checkLogLine(@NotNull ExternalSystemTaskId id,
                              @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                              @NotNull MavenLogEntryReader logEntryReader,
                              @NotNull Consumer<? super BuildEvent> messageConsumer) {
    if (completed) {
      return false;
    }

    String line = logLine.getLine();
    if (inScan && line.startsWith("--")) {
      messageConsumer.accept(new FinishEventImpl(NAME, id, System.currentTimeMillis(), NAME, new SuccessResultImpl()));
      completed = true;
      inScan = false;
    }
    if (line.equals("Scanning for projects...")) {
      messageConsumer.accept(new StartEventImpl(NAME, id, System.currentTimeMillis(), NAME));
      inScan = true;
    }

    return inScan;
  }

  @Override
  public void finish(@NotNull ExternalSystemTaskId taskId, @NotNull Consumer<? super BuildEvent> messageConsumer) {
    if (inScan) {
      messageConsumer.accept(new FinishEventImpl(NAME, taskId, System.currentTimeMillis(), NAME, new FailureResultImpl("Failed", null)));
    }
  }
}
