// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.build.BuildViewManager;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.impl.OutputBuildEventImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.build.output.BuildOutputInstantReaderImpl;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenResumeAction;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogOutputParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenOutputParserProvider;
import org.jetbrains.idea.maven.externalSystemIntegration.output.events.ArtifactDownloadScanning;
import org.jetbrains.idea.maven.externalSystemIntegration.output.events.BuildErrorNotification;
import org.jetbrains.idea.maven.externalSystemIntegration.output.events.ProjectScanning;
import org.jetbrains.idea.maven.externalSystemIntegration.output.events.WarningNotifier;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collections;

import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;

public class MavenBuildEventProcessor implements AnsiEscapeDecoder.ColoredTextAcceptor {
  @NotNull private final BuildViewManager myViewManager;
  @NotNull private final Project myProject;
  @NotNull private final BuildOutputInstantReader myInstantReader;
  @NotNull private final ExternalSystemTaskId myTaskId;
  @NotNull private final String myTitle;
  @NotNull private final String myWorkingDir;
  @NotNull private final MavenLogOutputParser myParser;

  public MavenBuildEventProcessor(@NotNull Project project,
                                  @NotNull String title,
                                  @NotNull String workingDir) {
    myViewManager = ServiceManager.getService(project, BuildViewManager.class);
    myProject = project;
    myTaskId = ExternalSystemTaskId.create(MavenConstants.SYSTEM_ID, EXECUTE_TASK, project);
    myTitle = title;
    myWorkingDir = workingDir;

    myParser = MavenOutputParserProvider.createMavenOutputParser(myTaskId);

    myInstantReader = new BuildOutputInstantReaderImpl(
      myTaskId,
      myViewManager,
      Collections.singletonList(myParser));
  }

  public void finish() {
    myInstantReader.close();
    myParser.finish(e -> myViewManager.onEvent(e));
  }

  public void start(@Nullable ExecutionEnvironment executionEnvironment, @Nullable ProcessHandler processHandler) {
    DefaultBuildDescriptor descriptor = new DefaultBuildDescriptor(myTaskId, myTitle, myWorkingDir, System.currentTimeMillis());
    StartBuildEventImpl startEvent = new StartBuildEventImpl(descriptor, "Maven run");
    if (executionEnvironment != null && processHandler != null) {
      startEvent
        .withRestartAction(new MavenResumeAction(processHandler, DefaultJavaProgramRunner.getInstance(), executionEnvironment));
    }

    myViewManager.onEvent(startEvent);
  }

  public void notifyException(Throwable throwable) {
    new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "Error running maven task", throwable.getMessage(), NotificationType.ERROR, null)
      .notify(myProject);
  }

  public void onTextAvailable(String text, boolean stdError) {
    myViewManager.onEvent(new OutputBuildEventImpl(myTaskId, text, !stdError));
    myInstantReader.append(text);
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key outputType) {
    boolean stdError = outputType == ProcessOutputTypes.STDERR;
    myViewManager.onEvent(new OutputBuildEventImpl(myTaskId, text, !stdError));
    myInstantReader.append(text);
  }
}
