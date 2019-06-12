// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.maven.server;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.eclipse.aether.RepositoryEvent;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;

import static org.eclipse.aether.RepositoryEvent.EventType.ARTIFACT_RESOLVED;
import static org.eclipse.aether.RepositoryEvent.EventType.ARTIFACT_RESOLVING;
import static org.jetbrains.maven.server.EventInfoPrinter.printMavenEventInfo;
import static org.jetbrains.maven.server.SpyConstants.NEWLINE;

@Named("Intellij Idea Maven Embedded Event Spy")
@Singleton
public class IntellijMavenSpy extends AbstractEventSpy {
  @Override
  public void onEvent(Object event) {
    try {
      if (event instanceof ExecutionEvent) {
        onExecutionEvent((ExecutionEvent)event);
      }
      else if (event instanceof RepositoryEvent) {
        onRepositoryEvent((RepositoryEvent)event);
      }
      else if (event instanceof DependencyResolutionRequest) {
        onDependencyResolutionRequest((DependencyResolutionRequest)event);
      }
      else if (event instanceof DependencyResolutionResult) {
        onDependencyResolutionResult((DependencyResolutionResult)event);
      }
      else {
        printMavenEventInfo("Unknown", "event", event);
      }
    }
    catch (Throwable e) {
      collectAndPrintLastLinesForEA(e);
    }
  }

  private static void onDependencyResolutionRequest(DependencyResolutionRequest event) {
    String projectId = event.getMavenProject() == null ? "unknown" : event.getMavenProject().getId();
    printMavenEventInfo("DependencyResolutionRequest", "id", projectId);
  }

  private static void onDependencyResolutionResult(DependencyResolutionResult event) {
    List<Exception> errors = event.getCollectionErrors();
    StringBuilder result = new StringBuilder();
    for (Exception e : errors) {
      if (result.length() > 0) {
        result.append(NEWLINE);
      }
      result.append(e.getMessage());
    }
    printMavenEventInfo("DependencyResolutionResult", "error", result);
  }

  private static void collectAndPrintLastLinesForEA(Throwable e) {
    //need to collect last 3 lines to send to EA
    int lines = Math.max(e.getStackTrace().length, 3);
    StringBuilder builder = new StringBuilder();
    builder.append(e.getMessage());
    for (int i = 0; i < lines; i++) {
      builder.append(e.getStackTrace()[i]).append("\n");
    }
    printMavenEventInfo("INTERR", "error", builder);
  }

  private static void onRepositoryEvent(RepositoryEvent event) {
    String errMessage = event.getException() == null ? "" : event.getException().getMessage();
    String path = event.getFile() == null ? "" : event.getFile().getPath();
    String artifactCoord = event.getArtifact() == null ? "" : event.getArtifact().toString();
    printMavenEventInfo(event.getType(), "path", path, "artifactCoord", artifactCoord, "error", errMessage);
  }

  private static void onExecutionEvent(ExecutionEvent event) {
    MojoExecution mojoExec = event.getMojoExecution();
    String projectId = event.getProject() == null ? "unknown" : event.getProject().getId();
    if (mojoExec != null) {
      String errMessage = event.getException() == null ? "" : event.getException().getMessage();
      printMavenEventInfo(event.getType(), "source", mojoExec.getSource(), "goal", mojoExec.getGoal(), "id", projectId, "error",
                          errMessage);
    }
    else {
      printMavenEventInfo(event.getType(), "id", projectId);
    }
  }
}
