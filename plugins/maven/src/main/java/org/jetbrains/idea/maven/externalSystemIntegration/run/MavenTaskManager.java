// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.run;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerConfiguration;
import com.intellij.openapi.externalSystem.task.BaseExternalSystemTaskManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.externalSystemIntegration.run.MavenExecutionStatusListener.MavenSubTask;
import org.jetbrains.idea.maven.externalSystemIntegration.settings.MavenExecutionSettings;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerDownloadListener;
import org.jetbrains.idea.maven.server.MavenServerExecutionResult;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MavenTaskManager extends BaseExternalSystemTaskManager<MavenExecutionSettings> {

  @Override
  public void executeTasks(@NotNull ExternalSystemTaskId id,
                           @NotNull List<String> taskNames,
                           @NotNull String projectPath,
                           @Nullable MavenExecutionSettings settings,
                           @Nullable String jvmAgentSetup,
                           @NotNull ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {
    MavenExecutionStatusListener mavenListener = new MavenExecutionStatusListener(id, listener);

    try {
      MavenSubTask startEvent = mavenListener.start("Starting maven server");

      ForkedDebuggerConfiguration forkedDebuggerSetup = ForkedDebuggerConfiguration.parse(jvmAgentSetup);
      if (forkedDebuggerSetup != null) {
        settings.withVmOption(forkedDebuggerSetup.getJvmAgentSetup(isJdk9orLater(settings.getJavaHome())));
      }

      Project project = id.findProject();
      if (project == null) {
        throw new RuntimeException("Cannot find project for task");
      }
      MavenServerManager serverManager = MavenServerManager.getInstance();


      MavenEmbedderWrapper embedder = serverManager.createEmbedder(project, false, projectPath, projectPath);
      customize(embedder, listener, id);
      startEvent.success();

      taskNames.forEach(taskName -> executeTask(settings, startEvent, embedder, taskName));
    }
    finally {
      mavenListener.failAllNonCompleted(new Exception("internal error"));
    }
  }

  private void executeTask(@NotNull MavenExecutionSettings settings,
                           MavenSubTask startEvent,
                           MavenEmbedderWrapper embedder, String taskName) {
    MavenSubTask execute = startEvent.startChild(taskName);
    try {

      MavenServerExecutionResult result = embedder
        .execute(settings.getPomFile(), Collections.emptyList(), Collections.emptyList(),
                 Collections.singletonList(taskName));

      if (result.problems != null && !result.problems.isEmpty()) {
        execute.failure(result.problems.stream().map(s -> s.getDescription()).collect(Collectors.toList()));
      }
      else {
        execute.success();
      }
    }
    catch (Exception e) {
      execute.failure(e);
    }
  }

  private void customize(MavenEmbedderWrapper embedder,
                         ExternalSystemTaskNotificationListener listener,
                         ExternalSystemTaskId id) {
    embedder.customizeForResolve(new MavenConsole(MavenExecutionOptions.LoggingLevel.DEBUG, false) {
      @Override
      public boolean canPause() {
        return false;
      }

      @Override
      public boolean isOutputPaused() {
        return false;
      }

      @Override
      public void setOutputPaused(boolean outputPaused) {
      }

      @Override
      public void attachToProcess(ProcessHandler processHandler) {
        MavenLog.LOG.debug("attachToProcess " + processHandler);
      }

      @Override
      protected void doPrint(String text, OutputType type) {
        MavenLog.LOG.debug(text);
        boolean stdErr = type == OutputType.ERROR;
        listener.onTaskOutput(id, text, !stdErr);
      }
    }, new MavenProgressIndicator());
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener)
    throws ExternalSystemException {
    return false;
  }
}
