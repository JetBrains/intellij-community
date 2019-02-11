// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.build.*;
import com.intellij.build.events.BuildEvent;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenConsoleImpl;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;

import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;

/**
 * implementation of maven console which sends data to build tool window.
 */
public class BuildToolWindowMavenConsole extends MavenConsole {
  private final MavenBuildEventProcessor myEventParser;

  public BuildToolWindowMavenConsole(@NotNull Project project, @NotNull String title, @NotNull String workingDir) {
    super(getSettings(project).getOutputLevel(), getSettings(project).isPrintErrorStackTraces());

    ExternalSystemTaskId taskId = ExternalSystemTaskId.create(MavenConstants.SYSTEM_ID, EXECUTE_TASK, project);
    DefaultBuildDescriptor descriptor =
      new DefaultBuildDescriptor(taskId, "Run Maven task", project.getBasePath(), System.currentTimeMillis());
    ConsoleView console = MavenConsoleImpl.createConsoleBuilder(project).getConsole();
    myEventParser =
      new MavenBuildEventProcessor(project, project.getBasePath(), createBuildView(project, console, descriptor), descriptor, taskId);
  }

  private static MavenGeneralSettings getSettings(Project project) {
    return MavenProjectsManager.getInstance(project).getGeneralSettings();
  }

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
    processHandler.addProcessListener(new BuildToolConsoleProcessAdapter(myEventParser));
  }

  /**
   * to be refactored
   *
   * @param project
   * @param console
   * @param descriptor
   */

  public static BuildView createBuildView(@NotNull Project project,
                                          @NotNull ExecutionConsole console,
                                          @NotNull BuildDescriptor descriptor) {

    return new BuildView(project, console, descriptor, "build.toolwindow.run.selection.state",
                         new ViewManager() {
                           @Override
                           public boolean isConsoleEnabledByDefault() {
                             return true;
                           }

                           @Override
                           public boolean isBuildContentView() {
                             return true;
                           }
                         });
  }

  @Override
  public void finish() {
    myEventParser.finish();
  }

  @Override
  public void printException(Throwable throwable) {
    super.printException(throwable);
    myEventParser.notifyException(throwable);
  }

  @Override
  protected void doPrint(String text, OutputType type) {
    myEventParser.onTextAvailable(text, type == OutputType.ERROR);
  }

}
