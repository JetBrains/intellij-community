// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.build.output.BuildOutputParser;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class MavenLogOutputParser implements BuildOutputParser {

  private boolean myCompleted = false;
  private final List<MavenLoggedEventParser> myRegisteredEvents;
  private final ExternalSystemTaskId myTaskId;

  public MavenLogOutputParser(ExternalSystemTaskId taskId,
                              List<MavenLoggedEventParser> registeredEvents) {
    myRegisteredEvents = registeredEvents;
    myTaskId = taskId;
  }


  public void finish(Consumer<? super BuildEvent> messageConsumer) {
    completeParsers(messageConsumer);

    if (!myCompleted) {
      messageConsumer
        .accept(new FinishBuildEventImpl(myTaskId, null, System.currentTimeMillis(), "Maven run",
                                         new FailureResultImpl(new Exception())));
    }
  }

  private void completeParsers(Consumer<? super BuildEvent> messageConsumer) {
    for (MavenLoggedEventParser parser : myRegisteredEvents) {
      parser.finish(myTaskId, messageConsumer);
    }
  }

  @Override
  public boolean parse(String line, BuildOutputInstantReader reader, Consumer<? super BuildEvent> messageConsumer) {
    if (myCompleted) return false;

    Pair<LogMessageType, String> logLine = nextLine(line);
    if (logLine == null) {
      return false;
    }


    for (MavenLoggedEventParser event : myRegisteredEvents) {
      if (!event.supportsType(logLine.first)) {
        continue;
      }
      if (event.checkLogLine(myTaskId, logLine.second, logLine.first, messageConsumer)) {
        return true;
      }
    }

    if (checkComplete(messageConsumer, logLine)) return true;
    return false;
  }

  private boolean checkComplete(Consumer<? super BuildEvent> messageConsumer, Pair<LogMessageType, String> logLine) {
    if (logLine.second.equals("BUILD FAILURE")) {
      completeParsers(messageConsumer);
      messageConsumer
        .accept(new FinishBuildEventImpl(myTaskId, null, System.currentTimeMillis(), "Maven run",
                                         new FailureResultImpl(new Exception())));
      myCompleted = true;
      return true;
    }
    if (logLine.second.equals("BUILD SUCCESS")) {
      completeParsers(messageConsumer);
      messageConsumer
        .accept(new FinishBuildEventImpl(myTaskId, null, System.currentTimeMillis(), "Maven run", new SuccessResultImpl()));
      myCompleted = true;
      return true;
    }
    return false;
  }

  @Nullable
  private static Pair<LogMessageType, String> nextLine(String line) {
    if (line == null) {
      return null;

    }
    line = clearProgressCarriageReturns(line);
    LogMessageType type = LogMessageType.determine(line);
    return Pair.create(type, clearLine(type, line));
  }

  @NotNull
  private static String clearProgressCarriageReturns(@NotNull String line) {
    int i = line.lastIndexOf("\r");
    if (i == -1) return line;
    return line.substring(i + 1);
  }

  @NotNull
  private static String clearLine(@Nullable LogMessageType type, @NotNull String line) {
    return type == null ? line : type.clearLine(line);
  }
}
