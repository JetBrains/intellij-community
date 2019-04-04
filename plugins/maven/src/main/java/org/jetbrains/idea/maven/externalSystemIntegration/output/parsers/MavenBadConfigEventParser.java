// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.BuildEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.events.MavenUnparseableConfigEvent;

import java.util.function.Consumer;

public class MavenBadConfigEventParser implements MavenLoggedEventParser {
  private static final String PREFIX = "Unable to parse maven.config:";

  @Override
  public boolean supportsType(@Nullable LogMessageType type) {
    return type == null;
  }

  @Override
  public boolean checkLogLine(@NotNull ExternalSystemTaskId id,
                              @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                              @NotNull MavenLogEntryReader logEntryReader,
                              @NotNull Consumer<? super BuildEvent> messageConsumer) {
    String line = logLine.getLine();
    if (line.startsWith(PREFIX)) {
      messageConsumer.accept(new MavenUnparseableConfigEvent(id, System.currentTimeMillis(), line.substring(PREFIX.length())));
      return true;
    }
    return false;
  }
}
