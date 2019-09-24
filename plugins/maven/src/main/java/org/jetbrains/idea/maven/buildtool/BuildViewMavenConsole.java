// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.build.*;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindowId;
import icons.MavenIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenConsoleImpl;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;

/**
 * Implementation of maven console which uses {@link BuildView} and can be displayed at:
 * <ul>
 * <li>`Build` tool window - for IDE build actions when the delegated mode is enabled
 * <li>`Run` tool window - for execution of {@link MavenRunConfiguration}s and other maven runs via {@link MavenRunner} e.g. `Run Anything`
 * </ul>
 */
@ApiStatus.Experimental
@Deprecated
/*
 * used only in MavenExternalExecutor
 * To be removed in IDEA-216278
 */
public class BuildViewMavenConsole extends MavenConsole {
  private final MavenBuildEventProcessor myEventParser;
  @NotNull
  private final Project myProject;
  @Nullable
  private final BuildView myBuildView;
  @NotNull
  private final String myTitle;
  private final long myExecutionId;
  private final AnsiEscapeDecoder myDecoder = new AnsiEscapeDecoder();

  public BuildViewMavenConsole(@NotNull Project project,
                               @NotNull String title,
                               @NotNull String workingDir,
                               @NotNull String toolWindowId,
                               long executionId) {
    super(getSettings(project).getOutputLevel(), getSettings(project).isPrintErrorStackTraces());
    myProject = project;
    myTitle = title;
    myExecutionId = executionId;
    ExternalSystemTaskId taskId = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, EXECUTE_TASK, project);
    DefaultBuildDescriptor descriptor = new DefaultBuildDescriptor(taskId, title, workingDir, System.currentTimeMillis());

    BuildProgressListener buildProgressListener;
    if (ToolWindowId.BUILD.equals(toolWindowId)) {
      myBuildView = null;
      buildProgressListener = ServiceManager.getService(project, BuildViewManager.class);
    }
    else if (ToolWindowId.RUN.equals(toolWindowId)) {
      ConsoleView console = MavenConsoleImpl.createConsoleBuilder(project).getConsole();
      myBuildView = createBuildView(project, console, descriptor);
      buildProgressListener = myBuildView;
    }
    else {
      throw new AssertionError("Unsupported toolwindow id: " + toolWindowId);
    }
    myEventParser = new MavenBuildEventProcessor(project, workingDir, buildProgressListener, descriptor, taskId, null);
  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    super.attachToProcess(processHandler);
    processHandler.addProcessListener(new BuildToolConsoleProcessAdapter(myEventParser, false));
    if (myBuildView != null) {
      myBuildView.attachToProcess(processHandler);
      ApplicationManager.getApplication().invokeLater(() -> {
        DefaultActionGroup actions = new DefaultActionGroup();
        actions.addAll((myBuildView).getSwitchActions());
        actions.add(BuildTreeFilters.createFilteringActionsGroup(myBuildView));
        JComponent consolePanel = createConsolePanel(myBuildView, actions);
        RunContentDescriptor descriptor =
          new RunContentDescriptor(myBuildView, processHandler, consolePanel, myTitle, MavenIcons.MavenLogo);
        descriptor.setExecutionId(myExecutionId);
        RunnerAndConfigurationSettings configuration = processHandler.getUserData(RunContentManagerImpl.TEMPORARY_CONFIGURATION_KEY);
        if (configuration != null) {
          ExecutionEnvironment environment =
            ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration.getConfiguration()).build();
          String toolWindowId =
            ExecutionManager.getInstance(myProject).getContentManager().getContentDescriptorToolWindowId(environment);
          if (toolWindowId != null) {
            descriptor.setContentToolWindowId(toolWindowId);
          }
        }
        Disposer.register(descriptor, myBuildView);
        ExecutionManager.getInstance(myProject).getContentManager().showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor);
      });
    }
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

  public void sendToEventParser(String text, Key outputType) {
    myDecoder.escapeText(text, outputType, myEventParser);
  }

  private static JComponent createConsolePanel(ConsoleView view, ActionGroup actions) {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(view.getComponent(), BorderLayout.CENTER);
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("MavenConsole", actions, false);
    JComponent toolbar = actionToolbar.getComponent();
    panel.add(toolbar, BorderLayout.WEST);
    return panel;
  }

  private static MavenGeneralSettings getSettings(Project project) {
    return MavenProjectsManager.getInstance(project).getGeneralSettings();
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

    return null;
  }
}
