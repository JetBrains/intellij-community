package org.jetbrains.idea.maven.embedder;

import com.intellij.execution.filters.*;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.MessageView;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.runner.MavenRunnerParameters;
import org.jetbrains.idea.maven.runner.MavenRunnerSettings;

import java.util.concurrent.atomic.AtomicBoolean;

public class MavenConsoleImpl extends MavenConsole {
  private static final Key<MavenConsoleImpl> CONSOLE_KEY = Key.create("MAVEN_CONSOLE_KEY");

  private static final String CONSOLE_FILTER_REGEXP =
    RegexpFilter.FILE_PATH_MACROS + ":\\[" + RegexpFilter.LINE_MACROS + "," + RegexpFilter.COLUMN_MACROS + "]";

  private final String myTitle;
  private final Project myProject;
  private final ConsoleView myConsoleView;
  private final AtomicBoolean isOpen = new AtomicBoolean(false);
  private final Pair<MavenRunnerParameters, MavenRunnerSettings> myParametersAndSettings;

  public MavenConsoleImpl(String title, Project project, MavenGeneralSettings coreSettings) {
    this(title, project, coreSettings, null);
  }

  public MavenConsoleImpl(String title,
                          Project project,
                          MavenGeneralSettings coreSettings,
                          Pair<MavenRunnerParameters, MavenRunnerSettings> parametersAndSettings) {
    super(coreSettings.getOutputLevel(), coreSettings.isPrintErrorStackTraces());
    myTitle = title;
    myProject = project;
    myConsoleView = createConsoleView();
    myParametersAndSettings = parametersAndSettings;
  }

  private ConsoleView createConsoleView() {
    TextConsoleBuilderFactory factory = TextConsoleBuilderFactory.getInstance();

    TextConsoleBuilder builder = factory.createBuilder(myProject);

    for (Filter filter : getFilters(myProject)) {
      builder.addFilter(filter);
    }

    return builder.getConsole();
  }

  private static Filter[] getFilters(final Project project) {
    return new Filter[]{new ExceptionFilter(project), new RegexpFilter(project, CONSOLE_FILTER_REGEXP)};
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

    MavenUtil.invokeLater(myProject, new Runnable() {
      public void run() {
        MessageView messageView = myProject.getComponent(MessageView.class);

        Content content = PeerFactory.getInstance().getContentFactory().createContent(
          myConsoleView.getComponent(), myTitle, true);
        content.putUserData(CONSOLE_KEY, MavenConsoleImpl.this);
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
            messageView.getContentManager().removeContent(each, false);
          }
        }

        ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
        if (!toolWindow.isActive())  {
          toolWindow.activate(null, false);
        }
      }
    });
  }

  public void close() {
    MessageView messageView = myProject.getComponent(MessageView.class);
    for (Content each : messageView.getContentManager().getContents()) {
      MavenConsoleImpl console = each.getUserData(CONSOLE_KEY);
      if (console != null) {
        messageView.getContentManager().removeContent(each, true);
        return;
      }
    }
  }
}