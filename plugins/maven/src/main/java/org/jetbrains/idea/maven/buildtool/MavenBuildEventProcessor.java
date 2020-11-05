// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.build.BuildDescriptor;
import com.intellij.build.BuildProgressListener;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.output.BuildOutputInstantReaderImpl;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogOutputParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenOutputParserProvider;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.project.MavenConsoleImpl;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collections;
import java.util.function.Function;

@ApiStatus.Experimental
public class MavenBuildEventProcessor implements AnsiEscapeDecoder.ColoredTextAcceptor {
  @NotNull private final BuildProgressListener myBuildProgressListener;
  @NotNull private final Project myProject;
  @NotNull private final BuildOutputInstantReaderImpl myInstantReader;
  @NotNull private final ExternalSystemTaskId myTaskId;
  @NotNull private final String myWorkingDir;
  @NotNull private final MavenLogOutputParser myParser;
  private boolean closed = false;
  private final BuildDescriptor myDescriptor;
  @NotNull private final Function<MavenParsingContext, StartBuildEvent> myStartBuildEventSupplier;

  public MavenBuildEventProcessor(@NotNull Project project,
                                  @NotNull String workingDir,
                                  @NotNull BuildProgressListener buildProgressListener,
                                  @NotNull BuildDescriptor descriptor,
                                  @NotNull ExternalSystemTaskId taskId,
                                  @NotNull Function<String, String> targetFileMapper,
                                  @Nullable Function<MavenParsingContext, StartBuildEvent> startBuildEventSupplier) {

    myBuildProgressListener = buildProgressListener;
    myProject = project;
    myTaskId = taskId;
    myWorkingDir = workingDir;
    myDescriptor = descriptor;
    myStartBuildEventSupplier = startBuildEventSupplier != null ? startBuildEventSupplier : ctx -> new StartBuildEventImpl(myDescriptor, "")
      .withExecutionFilters(MavenConsoleImpl.getMavenConsoleFilters(myProject));

    myParser = MavenOutputParserProvider.createMavenOutputParser(project, myTaskId, targetFileMapper);

    myInstantReader = new BuildOutputInstantReaderImpl(
      myTaskId, myTaskId,
      wrapListener(project, myBuildProgressListener, myWorkingDir),
      Collections.singletonList(myParser));
  }

  private static BuildProgressListener wrapListener(@NotNull Project project,
                                                    @NotNull BuildProgressListener listener,
                                                    @NotNull String workingDir) {
    return new MavenProgressListener(project, listener, workingDir);
  }

  public synchronized void finish() {
    myParser.finish(e -> myBuildProgressListener.onEvent(myDescriptor.getId(), e));
    myInstantReader.close();
    closed = true;
  }

  public void start() {
    StartBuildEvent startEvent = myStartBuildEventSupplier.apply(getParsingContext());

    myBuildProgressListener.onEvent(myDescriptor.getId(), startEvent);
  }

  public void notifyException(Throwable throwable) {
    new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, MavenProjectBundle.message("maven.build.messages.cannot.run.task"), throwable.getMessage(), NotificationType.ERROR, null)
      .notify(myProject);
  }

  public synchronized void onTextAvailable(String text, boolean stdError) {
    if (!closed) {
      myInstantReader.append(text);
    }
  }

  public MavenParsingContext getParsingContext() {
    return myParser.getParsingContext();
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key outputType) {
    onTextAvailable(text, ProcessOutputType.isStderr(outputType));
  }
}
