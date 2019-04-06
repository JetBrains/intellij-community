// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.FinishEventImpl;
import com.intellij.build.events.impl.StartEventImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;

import java.util.Set;
import java.util.function.Consumer;

public class ArtifactDownloadScanning implements MavenLoggedEventParser {
  private static final String DOWNLOADING = "Downloading";
  private static final String DOWNLOADED = "Downloaded";

  private final Set<String> startedToDownload = ContainerUtil.newHashSet();

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
    if (logLine.getLine().startsWith(DOWNLOADING)) {
      int resourceIdx = line.indexOf(':');
      if (resourceIdx < 0) {
        return false;
      }
      String resourceName = line.substring(resourceIdx + 1).trim();
      messageConsumer.accept(new StartEventImpl(getMessage(resourceName), id, System.currentTimeMillis(), getMessage(resourceName)));
      startedToDownload.add(resourceName);
      return true;
    }

    if (line.startsWith(DOWNLOADED)) {
      int openBracketIdx = line.indexOf("(");
      if (openBracketIdx < 0) return false;
      int resourceIdx = line.indexOf(':');
      if (resourceIdx < 0) {
        return false;
      }
      String resourceName = line.substring(resourceIdx + 1, openBracketIdx - 1).trim();
      if (startedToDownload.remove(resourceName)) {
        messageConsumer
          .accept(new FinishEventImpl(getMessage(resourceName), id, System.currentTimeMillis(), getMessage(resourceName),
                                      new SuccessResultImpl()));
        return true;
      }
    }
    return false;
  }

  @Override
  public void finish(@NotNull ExternalSystemTaskId taskId, @NotNull Consumer<? super BuildEvent> messageConsumer) {
    for (String resource : startedToDownload) {
      messageConsumer.accept(new FinishEventImpl(getMessage(resource), taskId, System.currentTimeMillis(), getMessage(resource),
                                    new FailureResultImpl("Cannot download artifact", null)));
    }
    startedToDownload.clear();
  }

  @NotNull
  private String getMessage(String resourceName) {
    return "Download " + resourceName;
  }
}

