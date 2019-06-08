// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.impl.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MavenSpyOutputParser {
  private final static String PREFIX = "[IJ]-";
  private final static String SEPARATOR = "-[IJ]-";
  private final static String NEWLINE = "-[N]-";
  private final IntObjectMap<MavenParsingContext.MavenExecutionEntry> threadProjectMap = ContainerUtil.createConcurrentIntObjectMap();
  private final MavenParsingContext myContext;

  public static boolean isSpyLog(String s) {
    return s != null && s.startsWith(PREFIX);
  }

  public MavenSpyOutputParser(MavenParsingContext context) {myContext = context;}


  public void processLine(@NotNull String spyLine,
                          @NotNull Consumer<? super BuildEvent> messageConsumer) {
    String line = spyLine.substring(PREFIX.length());
    try {
      int threadSeparatorIdx = line.indexOf('-');
      if (threadSeparatorIdx < 0) {
        return;
      }
      int threadId;
      try {
        threadId = Integer.parseInt(line.substring(0, threadSeparatorIdx));
      }
      catch (NumberFormatException ignore) {
        return;
      }
      if (threadId < 0) {
        return;
      }
      int typeSeparatorIdx = line.indexOf(SEPARATOR, threadSeparatorIdx + 1);
      if (typeSeparatorIdx < 0) {
        return;
      }
      String type = line.substring(threadSeparatorIdx + 1, typeSeparatorIdx);

      List<String> data = StringUtil.split(line.substring(typeSeparatorIdx + SEPARATOR.length()), SEPARATOR);
      Map<String, String> parameters =
        data.stream().map(d -> d.split("=")).filter(d -> d.length == 2).peek(d -> d[1] = d[1].replace(NEWLINE, "\n"))
          .collect(Collectors.toMap(d -> d[0], d -> d[1]));
      parse(threadId, type, parameters, messageConsumer);
    }
    catch (Exception e) {
      MavenLog.LOG.error(e);
    }
  }

  protected void parse(int threadId,
                       String type,
                       Map<String, String> parameters,
                       Consumer<? super BuildEvent> messageConsumer) {
    switch (type) {
      case "ProjectStarted": {
        MavenParsingContext.ProjectExecutionEntry execution = myContext.getProject(threadId, parameters, true);
        messageConsumer
          .accept(new StartEventImpl(execution.getId(), execution.getParentId(), System.currentTimeMillis(), execution.getName()));
        return;
      }
      case "MojoStarted": {
        MavenParsingContext.MojoExecutionEntry mojoExecution = myContext.getMojo(threadId, parameters, true);
        doStart(messageConsumer, mojoExecution);
        return;
      }
      case "MojoSucceeded": {
        MavenParsingContext.MojoExecutionEntry mojoExecution = myContext.getMojo(threadId, parameters, false);
        doSuccess(messageConsumer, mojoExecution);
        return;
      }
      case "MojoFailed": {
        MavenParsingContext.MojoExecutionEntry mojoExecution = myContext.getMojo(threadId, parameters, false);
        messageConsumer.accept(
          new FinishEventImpl(mojoExecution.getId(), mojoExecution.getParentId(), System.currentTimeMillis(), mojoExecution.getName(),
                              new FailureResultImpl(parameters.get("error"), null)));
        mojoExecution.complete();
        return;
      }
      case "MojoSkipped": {
        MavenParsingContext.MojoExecutionEntry mojoExecution = myContext.getMojo(threadId, parameters, false);
        doSkip(messageConsumer, mojoExecution);
        return;
      }
      case "ProjectSucceeded": {
        MavenParsingContext.ProjectExecutionEntry execution = myContext.getProject(threadId, parameters, false);
        doSuccess(messageConsumer, execution);
        return;
      }

      case "ProjectSkipped": {
        MavenParsingContext.ProjectExecutionEntry execution = myContext.getProject(threadId, parameters, false);
        doSkip(messageConsumer, execution);
        return;
      }

      case "ProjectFailed": {
        MavenParsingContext.ProjectExecutionEntry execution = myContext.getProject(threadId, parameters, false);
        doError(messageConsumer, execution, parameters.get("error"));
        return;
      }
      case "DependencyResolutionRequest": {
        MavenParsingContext.MojoExecutionEntry mojoExecution = myContext.getMojo(threadId, parameters, "Resolving Dependencies", true);
        doStart(messageConsumer, mojoExecution);
        return;
      }

      case "DependencyResolutionResult": {
        MavenParsingContext.MojoExecutionEntry mojoExecution = myContext.getMojo(threadId, parameters, "Resolving Dependencies", false);
        doSuccess(messageConsumer, mojoExecution);
        return;
      }

      case "ARTIFACT_DOWNLOADED": {
        String artifactCoord = parameters.get("artifactCoord");
        MavenParsingContext.NodeExecutionEntry execution = myContext.getNode(threadId, artifactCoord, false);
        String error = parameters.get("error");
        if (error != null) {
          doError(messageConsumer, execution, error);
        }
        else {
          doSuccess(messageConsumer, execution);
        }
        return;
      }

      case "ARTIFACT_DOWNLOADING": {
        String artifactCoord = parameters.get("artifactCoord");
        MavenParsingContext.NodeExecutionEntry execution = myContext.getNode(threadId, artifactCoord, true);
        doStart(messageConsumer, execution);
      }
    }
  }

  private static void doSkip(Consumer<? super BuildEvent> messageConsumer, MavenParsingContext.MavenExecutionEntry execution) {
    if (execution == null) {
      MavenLog.LOG.warn("Error parsing maven log");
      return;
    }
    messageConsumer
      .accept(new FinishEventImpl(execution.getId(), execution.getParentId(), System.currentTimeMillis(), execution.getName(),
                                  new SkippedResultImpl()));
    execution.complete();
  }

  private static void doStart(Consumer<? super BuildEvent> messageConsumer, MavenParsingContext.MavenExecutionEntry execution) {
    if (execution == null) {
      MavenLog.LOG.warn("Error parsing maven log");
      return;
    }
    messageConsumer
      .accept(
        new StartEventImpl(execution.getId(), execution.getParentId(), System.currentTimeMillis(), execution.getName()));
  }

  private static void doError(Consumer<? super BuildEvent> messageConsumer,
                              MavenParsingContext.MavenExecutionEntry execution,
                              String errorMessage) {
    if (execution == null) {
      MavenLog.LOG.warn("Error parsing maven log");
      return;
    }
    messageConsumer
      .accept(new FinishEventImpl(execution.getId(), execution.getParentId(), System.currentTimeMillis(), execution.getName(),
                                  new FailureResultImpl(errorMessage, null)));
    execution.complete();
  }

  private static void doSuccess(Consumer<? super BuildEvent> messageConsumer, MavenParsingContext.MavenExecutionEntry execution) {
    if (execution == null) {
      MavenLog.LOG.warn("Error parsing maven log");
      return;
    }
    messageConsumer
      .accept(
        new FinishEventImpl(execution.getId(), execution.getParentId(), System.currentTimeMillis(), execution.getName(),
                            new SuccessResultImpl(false)));
    execution.complete();
  }
}
