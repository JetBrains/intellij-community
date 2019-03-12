// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.events;

import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.FileMessageEventImpl;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;

import java.io.File;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildErrorNotification implements MavenLoggedEventParser {
  private static final Pattern LINE_AND_COLUMN = Pattern.compile("[^\\d]*?(\\d+)[^\\d]*(\\d*)[^\\d]*");

  @Override
  public boolean supportsType(@Nullable LogMessageType type) {
    return type == LogMessageType.ERROR;
  }

  @Override
  public boolean checkLogLine(@NotNull ExternalSystemTaskId id,
                              @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                              @NotNull MavenLogEntryReader logEntryReader,
                              @NotNull Consumer<? super BuildEvent> messageConsumer) {
    String line = logLine.getLine();
    int fileNameIdx = line.indexOf(".java:");
    if (fileNameIdx < 0) {
      return notifyError(id, line, messageConsumer);
    }
    int fullFileNameIdx = line.indexOf(":", fileNameIdx);
    if (fullFileNameIdx < 0) {
      return notifyError(id, line, messageConsumer);
    }
    File parsedFile = new File(line.substring(0, fileNameIdx) + ".java");
    int messageIdx = line.indexOf(' ', fullFileNameIdx);
    FilePosition position = withLineAndColumn(parsedFile, line.substring(fullFileNameIdx, messageIdx), messageIdx, fullFileNameIdx);
    messageConsumer
      .accept(new FileMessageEventImpl(id, MessageEvent.Kind.ERROR, COMPILER_MESSAGES_GROUP, line.substring(messageIdx), line,
                                       position));
    return true;
  }

  private static boolean notifyError(ExternalSystemTaskId id,
                                     String line,
                                     Consumer<? super BuildEvent> messageConsumer) {
    messageConsumer
      .accept(new MessageEventImpl(id, MessageEvent.Kind.ERROR, null, line, line));
    return true;
  }

  @NotNull
  private static FilePosition withLineAndColumn(File toTest, String line, int spaceAfterFileIdx, int fullFileNameIdx) {
    if (spaceAfterFileIdx < 0) return new FilePosition(toTest, 0, 0);
    Matcher matcher = LINE_AND_COLUMN.matcher(line);
    try {
      if (matcher.matches() && matcher.groupCount() == 2) {
        if (matcher.start(2) < 0) {
          return new FilePosition(toTest, Integer.valueOf(matcher.group(1)) - 1, 0);
        }
        else {
          return new FilePosition(toTest, Integer.valueOf(matcher.group(1)) - 1, Integer.valueOf(matcher.group(2)) - 1);
        }
      }

      return new FilePosition(toTest, 0, 0);
    }
    catch (NumberFormatException ignore) {
      return new FilePosition(toTest, 0, 0);
    }
  }
}
