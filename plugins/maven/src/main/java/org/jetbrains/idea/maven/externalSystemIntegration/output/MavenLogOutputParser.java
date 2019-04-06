// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.build.output.BuildOutputParser;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;


@ApiStatus.Experimental
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

    MavenLogEntryReader.MavenLogEntry logLine = nextLine(line);
    if (logLine == null || StringUtil.isEmptyOrSpaces(logLine.myLine)) {
      return false;
    }

    MavenLogEntryReader mavenLogReader = wrapReader(reader);
    for (MavenLoggedEventParser event : myRegisteredEvents) {
      if (!event.supportsType(logLine.myType)) {
        continue;
      }
      if (event.checkLogLine(myTaskId, logLine, mavenLogReader, messageConsumer)) {
        return true;
      }
    }

    if (checkComplete(messageConsumer, logLine, mavenLogReader)) return true;
    return false;
  }

  private static MavenLogEntryReader wrapReader(BuildOutputInstantReader reader) {
    return new MavenLogEntryReader() {
      @Override
      public void pushBack() {
        reader.pushBack();
      }

      @Nullable
      @Override
      public MavenLogEntry readLine() {
        return nextLine(reader.readLine());
      }
    };
  }

  private boolean checkComplete(Consumer<? super BuildEvent> messageConsumer,
                                MavenLogEntryReader.MavenLogEntry logLine,
                                MavenLogEntryReader mavenLogReader) {
    if (logLine.myLine.equals("BUILD FAILURE")) {
      MavenLogEntryReader.MavenLogEntry errorDesc = mavenLogReader.findFirst(s -> s.getType() == LogMessageType.ERROR);
      completeParsers(messageConsumer);
      messageConsumer
        .accept(new FinishBuildEventImpl(myTaskId, null, System.currentTimeMillis(), "Maven run",
                                         new FailureResultImpl(errorDesc == null ? "Failed" : errorDesc.myLine,
                                                               null)));
      myCompleted = true;
      return true;
    }
    if (logLine.myLine.equals("BUILD SUCCESS")) {
      completeParsers(messageConsumer);
      messageConsumer
        .accept(new FinishBuildEventImpl(myTaskId, null, System.currentTimeMillis(), "Maven run", new SuccessResultImpl()));
      myCompleted = true;
      return true;
    }
    return false;
  }

  @Nullable
  private static MavenLogEntryReader.MavenLogEntry nextLine(String line) {
    if (line == null) {
      return null;
    }
    return new MavenLogEntryReader.MavenLogEntry(line);
  }
}
