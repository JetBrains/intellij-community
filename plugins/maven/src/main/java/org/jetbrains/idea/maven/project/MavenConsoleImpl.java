/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.MessageView;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class MavenConsoleImpl extends MavenConsole {
  private static final Key<MavenConsoleImpl> CONSOLE_KEY = Key.create("MAVEN_CONSOLE_KEY");

  private static final String CONSOLE_FILTER_REGEXP =
    "(?:^|(?:\\[\\w+\\]\\s*))" + RegexpFilter.FILE_PATH_MACROS + ":\\[" + RegexpFilter.LINE_MACROS + "," + RegexpFilter.COLUMN_MACROS + "]";

  private final String myTitle;
  private final Project myProject;
  private final ConsoleView myConsoleView;
  private final AtomicBoolean isOpen = new AtomicBoolean(false);
  private final Pair<MavenRunnerParameters, MavenRunnerSettings> myParametersAndSettings;

  public MavenConsoleImpl(String title, Project project) {
    this(title, project, null);
  }

  public MavenConsoleImpl(String title,
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

  private ConsoleView createConsoleView() {
    return createConsoleBuilder(myProject).getConsole();
  }

  public static TextConsoleBuilder createConsoleBuilder(final Project project) {
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    builder.addFilter(new RegexpFilter(project, CONSOLE_FILTER_REGEXP) {
      @Nullable
      @Override
      protected HyperlinkInfo createOpenFileHyperlink(String fileName, int line, int column) {
        HyperlinkInfo res = super.createOpenFileHyperlink(fileName, line, column);
        if (res == null && fileName.startsWith("\\") && SystemInfo.isWindows) {
          // Maven cut prefix 'C:\' from paths on Windows
          VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
          if (roots.length > 0) {
            String projectPath = roots[0].getPath();
            if (projectPath.matches("[A-Z]:[\\\\/].+")) {
              res = super.createOpenFileHyperlink(projectPath.charAt(0) + ":" + fileName, line, column);
            }
          }

        }

        return res;
      }
    });

    builder.addFilter(new MavenGroovyConsoleFilter(project));
    builder.addFilter(new MavenScalaConsoleFilter(project));
    builder.addFilter(new MavenTestConsoleFilter());
    return builder;
  }

  public boolean canPause() {
    return myConsoleView.canPause();
  }

  public boolean isOutputPaused() {
    return myConsoleView.isOutputPaused();
  }

  public void setOutputPaused(boolean outputPaused) {
    myConsoleView.setOutputPaused(outputPaused);
  }

  public Pair<MavenRunnerParameters, MavenRunnerSettings> getParametersAndSettings() {
    return myParametersAndSettings;
  }

  public void attachToProcess(ProcessHandler processHandler) {
    myConsoleView.attachToProcess(processHandler);
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        ensureAttachedToToolWindow();
      }
    });
  }

  protected void doPrint(String text, OutputType type) {
    ensureAttachedToToolWindow();

    ConsoleViewContentType contentType;
    switch (type) {
      case SYSTEM:
        contentType = ConsoleViewContentType.SYSTEM_OUTPUT;
        break;
      case ERROR:
        contentType = ConsoleViewContentType.ERROR_OUTPUT;
        break;
      case NORMAL:
      default:
        contentType = ConsoleViewContentType.NORMAL_OUTPUT;
    }
    myConsoleView.print(text, contentType);
  }

  private void ensureAttachedToToolWindow() {
    if (!isOpen.compareAndSet(false, true)) return;

    MavenUtil.invokeLater(myProject, () -> {
      MessageView messageView = MessageView.SERVICE.getInstance(myProject);

      Content content = ContentFactory.SERVICE.getInstance().createContent(
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
    MessageView messageView = MessageView.SERVICE.getInstance(myProject);
    for (Content each : messageView.getContentManager().getContents()) {
      MavenConsoleImpl console = each.getUserData(CONSOLE_KEY);
      if (console != null) {
        messageView.getContentManager().removeContent(each, true);
        return;
      }
    }
  }
}
