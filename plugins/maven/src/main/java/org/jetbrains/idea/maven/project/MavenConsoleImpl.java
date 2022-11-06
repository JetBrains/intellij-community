// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.MessageView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @deprecated to be removed when build tools will be active
 */
@Deprecated(forRemoval = true)
public class MavenConsoleImpl extends MavenConsole {
  private static final Key<MavenConsoleImpl> CONSOLE_KEY = Key.create("MAVEN_CONSOLE_KEY");

  private static final String CONSOLE_FILTER_REGEXP =
    "(?:^|(?:\\[\\w+\\]\\s*)( /)?)" + RegexpFilter.FILE_PATH_MACROS + ":\\[" + RegexpFilter.LINE_MACROS + "," + RegexpFilter.COLUMN_MACROS + "]";

  private @NlsContexts.TabTitle final String myTitle;
  private final Project myProject;
  private final ConsoleView myConsoleView;
  private final AtomicBoolean isOpen = new AtomicBoolean(false);
  private final Pair<MavenRunnerParameters, MavenRunnerSettings> myParametersAndSettings;

  public MavenConsoleImpl(@NlsContexts.TabTitle String title, Project project) {
    this(title, project, null);
  }

  public MavenConsoleImpl(@NlsContexts.TabTitle String title,
                          Project project,
                          Pair<MavenRunnerParameters, MavenRunnerSettings> parametersAndSettings) {
    super(getGeneralSettings(project).getLoggingLevel(), getGeneralSettings(project).isPrintErrorStackTraces());
    myTitle = title;
    myProject = project;
    myConsoleView = createConsoleView();
    myParametersAndSettings = parametersAndSettings;
  }

  private static MavenGeneralSettings getGeneralSettings(Project project) {
    return MavenProjectsManager.getInstance(project).getGeneralSettings();
  }

  public ConsoleView createConsoleView() {
    return createConsoleBuilder(myProject).getConsole();
  }

  /**
   * to be refactored
   */
  public static TextConsoleBuilder createConsoleBuilder(final Project project) {
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    return builder;
  }

  @Override
  public boolean canPause() {
    return myConsoleView.canPause();
  }

  @Override
  public boolean isOutputPaused() {
    return myConsoleView.isOutputPaused();
  }

  @Override
  public void setOutputPaused(boolean outputPaused) {
    myConsoleView.setOutputPaused(outputPaused);
  }

  public Pair<MavenRunnerParameters, MavenRunnerSettings> getParametersAndSettings() {
    return myParametersAndSettings;
  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    myConsoleView.attachToProcess(processHandler);
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        ensureAttachedToToolWindow();
      }
    });
  }

  @Override
  protected void doPrint(String text, OutputType type) {
    ensureAttachedToToolWindow();

    ConsoleViewContentType contentType = switch (type) {
      case SYSTEM -> ConsoleViewContentType.SYSTEM_OUTPUT;
      case ERROR -> ConsoleViewContentType.ERROR_OUTPUT;
      case NORMAL -> ConsoleViewContentType.NORMAL_OUTPUT;
    };
    myConsoleView.print(text, contentType);
  }

  private void ensureAttachedToToolWindow() {
    if (!isOpen.compareAndSet(false, true)) return;

    MavenUtil.invokeLater(myProject, () -> {
      MessageView messageView = MessageView.getInstance(myProject);

      Content content = ContentFactory.getInstance().createContent(
        myConsoleView.getComponent(), myTitle, true);
      content.putUserData(CONSOLE_KEY, this);
      messageView.getContentManager().addContent(content);
      messageView.getContentManager().setSelectedContent(content);

      // remove unused tabs
      for (Content each : messageView.getContentManager().getContents()) {
        if (each.isPinned()) continue;
        if (each == content) continue;

        MavenConsoleImpl console = each.getUserData(CONSOLE_KEY);
        if (console == null) continue;

        if (!myTitle.equals(console.myTitle)) continue;

        if (console.isFinished()) {
          messageView.getContentManager().removeContent(each, true);
        }
      }

      ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
      if (!toolWindow.isActive()) {
        toolWindow.activate(null, false);
      }
    });
  }

  public void close() {
    MessageView messageView = MessageView.getInstance(myProject);
    for (Content each : messageView.getContentManager().getContents()) {
      MavenConsoleImpl console = each.getUserData(CONSOLE_KEY);
      if (console != null) {
        messageView.getContentManager().removeContent(each, true);
        return;
      }
    }
  }
}
