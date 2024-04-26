// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.impl.*;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenSpyLoggedEventParser;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MavenSpyOutputParser {
  public static final String PREFIX = "[IJ]-";
  private static final String SEPARATOR = "-[IJ]-";
  private static final String NEWLINE = "-[N]-";
  private static final String DOWNLOAD_DEPENDENCIES_NAME = "dependencies";
  private final Set<String> downloadingMap = new HashSet<>();
  private final MavenParsingContext myContext;

  public static boolean isSpyLog(String s) {
    return s != null && s.startsWith(PREFIX);
  }

  public MavenSpyOutputParser(@NotNull MavenParsingContext context) {
    myContext = context;
  }


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

      MavenEventType eventType = MavenEventType.valueByName(type);
      if (eventType == null) {
        return;
      }
      processErrorLogLine(parameters.get("error"), eventType, messageConsumer);
      parse(threadId, eventType, parameters, messageConsumer);
    }
    catch (Exception e) {
      MavenLog.LOG.error("Error processing line " + spyLine, e);
    }
  }

  protected void parse(int threadId,
                       MavenEventType type,
                       Map<String, String> parameters,
                       Consumer<? super BuildEvent> messageConsumer) {
    switch (type) {
      case SESSION_STARTED -> {
        List<String> projectsInReactor = getProjectsInReactor(parameters);
        myContext.setProjectsInReactor(projectsInReactor);
      }
      case SESSION_ENDED -> doFinishSession(messageConsumer, myContext);
      case PROJECT_STARTED -> {
        MavenParsingContext.ProjectExecutionEntry execution = myContext.getProject(threadId, parameters, true);
        if (execution == null) {
          MavenLog.LOG.debug("Not found for " + parameters);
        }
        else {
          messageConsumer
            .accept(new StartEventImpl(execution.getId(), execution.getParentId(), System.currentTimeMillis(), execution.getName()));
        }
      }
      case MOJO_STARTED -> {
        MavenParsingContext.MojoExecutionEntry mojoExecution = myContext.getMojo(threadId, parameters, true);
        doStart(messageConsumer, mojoExecution);
      }
      case MOJO_SUCCEEDED -> {
        stopFakeDownloadNode(threadId, parameters, messageConsumer);
        MavenParsingContext.MojoExecutionEntry mojoExecution = myContext.getMojo(threadId, parameters, false);
        doComplete(messageConsumer, mojoExecution);
      }
      case MOJO_FAILED -> {
        stopFakeDownloadNode(threadId, parameters, messageConsumer);
        MavenParsingContext.MojoExecutionEntry mojoExecution = myContext.getMojo(threadId, parameters, false);
        if (mojoExecution == null) {
          MavenLog.LOG.debug("Not found id for " + parameters);
        }
        else {
          messageConsumer.accept(
            new FinishEventImpl(mojoExecution.getId(), mojoExecution.getParentId(), System.currentTimeMillis(), mojoExecution.getName(),
                                new MavenTaskFailedResultImpl(parameters.get("error"))));
          mojoExecution.complete();
        }
      }
      case MOJO_SKIPPED -> {
        stopFakeDownloadNode(threadId, parameters, messageConsumer);
        MavenParsingContext.MojoExecutionEntry mojoExecution = myContext.getMojo(threadId, parameters, false);
        doSkip(messageConsumer, mojoExecution);
      }
      case PROJECT_SUCCEEDED -> {
        stopFakeDownloadNode(threadId, parameters, messageConsumer);
        MavenParsingContext.ProjectExecutionEntry execution = myContext.getProject(threadId, parameters, false);
        doComplete(messageConsumer, execution);
      }
      case PROJECT_SKIPPED -> {
        MavenParsingContext.ProjectExecutionEntry execution = myContext.getProject(threadId, parameters, false);
        doSkip(messageConsumer, execution);
      }
      case PROJECT_FAILED -> {
        stopFakeDownloadNode(threadId, parameters, messageConsumer);
        MavenParsingContext.ProjectExecutionEntry execution = myContext.getProject(threadId, parameters, false);
        myContext.setProjectFailure(true);
        doError(messageConsumer, execution, parameters.get("error"));
      }
      case ARTIFACT_RESOLVED -> artifactResolved(threadId, parameters, messageConsumer);
      case ARTIFACT_DOWNLOADING -> artifactDownloading(threadId, parameters, messageConsumer);
    }
  }

  private void processErrorLogLine(String errorLine,
                                   MavenEventType eventType,
                                   Consumer<? super BuildEvent> messageConsumer) {
    if (errorLine == null) return;
    for (MavenSpyLoggedEventParser eventParser : MavenSpyLoggedEventParser.EP_NAME.getExtensionList()) {
      if (eventParser.supportsType(eventType)
          && eventParser.processLogLine(myContext.getLastId(), myContext, errorLine, messageConsumer)) {
        return;
      }
    }
  }

  private static List<String> getProjectsInReactor(Map<String, String> parameters) {
    String joined = parameters.get("projects");
    if (StringUtil.isEmptyOrSpaces(joined)) {
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<>();
    for (String project : joined.split("&&")) {
      if (StringUtil.isEmptyOrSpaces(project)) {
        continue;
      }
      result.add(project);
    }
    return result;
  }

  private void artifactDownloading(int threadId, Map<String, String> parameters, Consumer<? super BuildEvent> messageConsumer) {
    @NlsSafe String artifactCoord = parameters.get("artifactCoord");
    if (artifactCoord == null || !downloadingMap.add(artifactCoord)) {
      return;
    }

    MavenParsingContext.MavenExecutionEntry parent = startFakeDownloadNodeIfNotStarted(threadId, parameters, messageConsumer);

    messageConsumer
      .accept(
        new StartEventImpl(getDownloadId(artifactCoord), parent.getId(), System.currentTimeMillis(), artifactCoord));
  }

  private void artifactResolved(int threadId, Map<String, String> parameters, Consumer<? super BuildEvent> messageConsumer) {
    @NlsSafe String artifactCoord = parameters.get("artifactCoord");
    if (artifactCoord == null) {
      return;
    }
    @NlsSafe String error = parameters.get("error");
    if (error != null || downloadingMap.contains(artifactCoord)) {
      MavenParsingContext.MavenExecutionEntry parent = startFakeDownloadNodeIfNotStarted(threadId, parameters, messageConsumer);
      if (error != null) {
        if (downloadingMap.remove(artifactCoord)) {
          messageConsumer
            .accept(new FinishEventImpl(getDownloadId(artifactCoord), parent.getId(), System.currentTimeMillis(), artifactCoord,
                                        new FailureResultImpl(error, null)));
        }
        else {
          Object eventId = new Object();
          messageConsumer
            .accept(new StartEventImpl(eventId, parent.getId(), System.currentTimeMillis(), error));
          messageConsumer
            .accept(new FinishEventImpl(eventId, parent.getId(), System.currentTimeMillis(), error, new FailureResultImpl()));
        }
      }
      else {
        messageConsumer
          .accept(new FinishEventImpl(getDownloadId(artifactCoord), parent.getId(), System.currentTimeMillis(), artifactCoord,
                                      new SuccessResultImpl(false)));
      }
    }
  }

  @NotNull
  private static String getDownloadId(String artifactCoord) {
    return "download" + artifactCoord;
  }

  private MavenParsingContext.MavenExecutionEntry startFakeDownloadNodeIfNotStarted(int threadId,
                                                                                    Map<String, String> parameters,
                                                                                    Consumer<? super BuildEvent> messageConsumer) {
    MavenParsingContext.NodeExecutionEntry parentMojo = myContext.getNode(threadId, DOWNLOAD_DEPENDENCIES_NAME, false);
    if (parentMojo != null) {
      return parentMojo;
    }
    parentMojo = myContext.getNode(threadId, DOWNLOAD_DEPENDENCIES_NAME, true);
    doStart(messageConsumer, parentMojo);
    return parentMojo;
  }

  private void stopFakeDownloadNode(int threadId, Map<String, String> parameters, Consumer<? super BuildEvent> messageConsumer) {
    MavenParsingContext.MavenExecutionEntry parentMojo = myContext.getNode(threadId, DOWNLOAD_DEPENDENCIES_NAME, false);
    if (parentMojo != null) {
      doComplete(messageConsumer, parentMojo);
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
                                  new MavenTaskFailedResultImpl(errorMessage)));
    execution.complete();
  }

  private static void doComplete(Consumer<? super BuildEvent> messageConsumer, MavenParsingContext.MavenExecutionEntry execution) {
    if (execution == null) {
      MavenLog.LOG.warn("Error parsing maven log");
      return;
    }
    messageConsumer
      .accept(
        new FinishEventImpl(execution.getId(), execution.getParentId(), System.currentTimeMillis(), execution.getName(),
                            new SuccessResultImpl()));
    execution.complete();
  }

  private static void doFinishSession(Consumer<? super BuildEvent> messageConsumer, MavenParsingContext context) {
    context.setSessionEnded(true);
    if (context.getProjectFailure()) {
      messageConsumer
        .accept(new FinishBuildEventImpl(context.getMyTaskId(), null, System.currentTimeMillis(), "", new FailureResultImpl()));
    }
    else {
      messageConsumer
        .accept(new FinishBuildEventImpl(context.getMyTaskId(), null, System.currentTimeMillis(), "", new SuccessResultImpl()));
    }
  }
}
