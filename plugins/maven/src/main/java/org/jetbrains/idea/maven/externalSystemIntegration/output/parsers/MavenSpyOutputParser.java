// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.Failure;
import com.intellij.build.events.impl.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MavenSpyOutputParser implements MavenLoggedEventParser {
  private final static String PREFIX = "-[IJ]-";
  private final static String NEWLINE = "-[N]-";
  private final IntObjectMap<ProjectStatus> threadProjectMap = ContainerUtil.createConcurrentIntObjectMap();

  @Override
  public boolean supportsType(@Nullable LogMessageType type) {
    return type == LogMessageType.IJ;
  }

  @Override
  public boolean checkLogLine(@NotNull ExternalSystemTaskId id,
                              @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                              @NotNull MavenLogEntryReader logEntryReader,
                              @NotNull Consumer<? super BuildEvent> messageConsumer) {
    String line = logLine.getLine();
    try {
      int threadSeparatorIdx = line.indexOf('-');
      int threadId = Integer.parseInt(line.substring(0, threadSeparatorIdx));
      int typeSeparatorIdx = line.indexOf(PREFIX, threadSeparatorIdx + 1);
      if (typeSeparatorIdx < 0) {
        return false;
      }
      String type = line.substring(threadSeparatorIdx + 1, typeSeparatorIdx);

      List<String> data = StringUtil.split(line.substring(typeSeparatorIdx + PREFIX.length()), PREFIX);
      Map<String, String> parameters =
        data.stream().map(d -> d.split("=")).filter(d -> d.length == 2).peek(d -> d[1] = d[1].replace(NEWLINE, "\n"))
          .collect(Collectors.toMap(d -> d[0], d -> d[1]));
      return parse(threadId, type, parameters, id, messageConsumer);
    }
    catch (Exception e) {
      MavenLog.LOG.error(e);
      return false;
    }
  }

  protected boolean parse(int threadId,
                          String type,
                          Map<String, String> parameters,
                          ExternalSystemTaskId id,
                          Consumer<? super BuildEvent> messageConsumer) {
    switch (type) {
      case "ProjectStarted": {
        String projectId = parameters.get("id");
        messageConsumer.accept(new StartEventImpl(projectId, id, System.currentTimeMillis(), "Project " + projectId));
        threadProjectMap.put(threadId, new ProjectStatus(projectId));
        return true;
      }
      case "MojoStarted": {
        String projectId = threadProjectMap.get(threadId).myName;
        messageConsumer.accept(new StartEventImpl(parameters.get("goal"), projectId, System.currentTimeMillis(), parameters.get("goal")));
        return true;
      }
      case "MojoSucceeded": {
        String projectId = threadProjectMap.get(threadId).myName;
        messageConsumer
          .accept(new FinishEventImpl(parameters.get("goal"), projectId, System.currentTimeMillis(), parameters.get("goal"),
                                      new SuccessResultImpl(false)));
        return true;
      }
      case "MojoFailed": {
        String projectId = threadProjectMap.get(threadId).myName;
        String error = parameters.get("error");
        messageConsumer.accept(new FinishEventImpl(parameters.get("goal"), projectId, System.currentTimeMillis(), parameters.get("goal"),
                                                   new FailureResultImpl(parameters.get("error"), null)));
        threadProjectMap.get(threadId).addFailure(new FailureImpl(error, (Throwable)null));
        return true;
      }
      case "MojoSkipped": {
        String projectId = threadProjectMap.get(threadId).myName;
        messageConsumer.accept(new FinishEventImpl(parameters.get("goal"), projectId, System.currentTimeMillis(), parameters.get("goal"),
                                                   new SkippedResultImpl()));
        return true;
      }
      case "ProjectSucceeded": {
        String projectId = parameters.get("id");
        messageConsumer.accept(new FinishEventImpl(projectId, id, System.currentTimeMillis(), "Project " + projectId,
                                                   new SuccessResultImpl(false)));
        return true;
      }

      case "ProjectSkipped": {
        String projectId = parameters.get("id");
        messageConsumer.accept(new FinishEventImpl(projectId, id, System.currentTimeMillis(), "Project " + projectId,
                                                   new SkippedResultImpl()));
        return true;
      }

      case "ProjectFailed": {
        String projectId = parameters.get("id");
        messageConsumer.accept(new FinishEventImpl(projectId, id, System.currentTimeMillis(), "Project " + projectId,
                                                   new SuccessResultImpl()));
        //new FailureResultImpl(threadProjectMap.get(threadId).myFailures)));
        return true;
      }
      case "DependencyResolutionRequest": {
        String projectId = threadProjectMap.get(threadId).myName;
        messageConsumer.accept(
          new StartEventImpl("Resolving Dependencies" + projectId, projectId, System.currentTimeMillis(), "Resolving Dependencies"));
        return true;
      }
      case "DependencyResolutionResult": {
        String projectId = threadProjectMap.get(threadId).myName;
        String error = parameters.get("error");
        if (StringUtil.isEmpty(error)) {
          messageConsumer
            .accept(
              new FinishEventImpl("Resolving Dependencies" + projectId, projectId, System.currentTimeMillis(), "Resolving Dependencies",
                                  new SuccessResultImpl()));
        }
        else {
          List<Failure> failureResults = ContainerUtil.map(error.split("\n"), s -> new FailureImpl(s, (Throwable)null));
          messageConsumer.accept(
            new FinishEventImpl("Resolving Dependencies" + projectId, projectId, System.currentTimeMillis(), "Resolving Dependencies",
                                new FailureResultImpl(failureResults)));
          threadProjectMap.get(threadId).addFailures(failureResults);
        }
        return true;
      }

      case "ARTIFACT_DOWNLOADED": {
        String projectId = threadProjectMap.get(threadId).myName;
        String artifactCoord = parameters.get("artifactCoord");
        String error = parameters.get("error");
        if (StringUtil.isEmpty(error)) {
          messageConsumer
            .accept(
              new FinishEventImpl(projectId + " download " + artifactCoord, "Resolving Dependencies" + projectId, System.currentTimeMillis(),
                                  "Download " + artifactCoord,
                                  new SuccessResultImpl()));
        }
        else {
          List<Failure> failureResults = ContainerUtil.map(error.split("\n"), s -> new FailureImpl(s, (Throwable)null));
          messageConsumer.accept(
            new FinishEventImpl(projectId + " download " + artifactCoord, "Resolving Dependencies" + projectId, System.currentTimeMillis(),
                                "Download " + artifactCoord,
                                new FailureResultImpl(failureResults)));
          threadProjectMap.get(threadId).addFailures(failureResults);
        }
        return true;
      }

      case "ARTIFACT_DOWNLOADING": {
        String projectId = threadProjectMap.get(threadId).myName;
        String artifactCoord = parameters.get("artifactCoord");
        messageConsumer.accept(
          new StartEventImpl(projectId + " download " + artifactCoord, "Resolving Dependencies" + projectId, System.currentTimeMillis(),
                             "Download " + artifactCoord));
        return true;
      }

      default:
        return false;
    }
  }

  private static class ProjectStatus {
    String myName;
    List<Failure> myFailures = null;

    ProjectStatus(String name) {
      myName = name;
    }

    public void addFailure(Failure failure) {
      if (myFailures == null) {
        myFailures = new ArrayList<>();
      }
      myFailures.add(failure);
    }

    public void addFailures(List<Failure> failures) {
      if (myFailures == null) {
        myFailures = new ArrayList<>();
      }
      myFailures.addAll(failures);
    }
  }
}
