package org.jetbrains.idea.maven.runner;

import com.intellij.execution.filters.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.application.ApplicationManager;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.DummyProjectComponent;

import java.util.ArrayList;
import java.util.List;

@State(name = "MavenRunner", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenRunner extends DummyProjectComponent implements PersistentStateComponent<MavenRunnerSettings> {
  @NonNls private static final String OUTPUT_TOOL_WINDOW_ID = "Maven Runner Output";

  private static final String CONSOLE_FILTER_REGEXP =
      RegexpFilter.FILE_PATH_MACROS + ":\\[" + RegexpFilter.LINE_MACROS + "," + RegexpFilter.COLUMN_MACROS + "]";

  private final Project myProject;
  private final MavenCore myMavenCore;

  private MavenRunnerSettings mySettings = new MavenRunnerSettings();
  private Pair<MavenRunnerParameters, MavenRunnerSettings> myLastRunnerParametersAndSettings;

  private MavenExecutor myExecutor;
  private MavenRunnerOutputPanel myMavenOutputWindowPanel;

  public static MavenRunner getInstance(Project project) {
    return project.getComponent(MavenRunner.class);
  }

  public MavenRunner(final Project project, MavenCore mavenCore) {
    super("MavenRunner");

    this.myProject = project;
    this.myMavenCore = mavenCore;

    mavenCore.addConfigurableFactory(new MavenCore.ConfigurableFactory() {
      public Configurable createConfigurable() {
        return new MavenRunnerConfigurable(project, false) {
          protected MavenRunnerSettings getState() {
            return mySettings;
          }
        };
      }
    });
  }

  public MavenRunnerSettings getState() {
    return mySettings;
  }

  public void loadState(MavenRunnerSettings settings) {
    mySettings = settings;
  }

  public MavenExecutor getExecutor() {
    return myExecutor;
  }

  public boolean isToolWindowOpen() {
    return ToolWindowManager.getInstance(myProject).getToolWindow(OUTPUT_TOOL_WINDOW_ID) != null;
  }

  public void closeToolWindow() {
    if (isToolWindowOpen()) {
      ToolWindowManager.getInstance(myProject).unregisterToolWindow(OUTPUT_TOOL_WINDOW_ID);
    }
  }

  public boolean isRunning() {
    return myExecutor != null && !myExecutor.isStopped();
  }

  public void run(MavenRunnerParameters parameters) {
    run(parameters, mySettings);
  }

  public void run(MavenRunnerParameters parameters, MavenRunnerSettings settings) {
    run(parameters, settings, null);
  }

  public void run(final MavenRunnerParameters parameters, final MavenRunnerSettings settings, final Runnable onComplete) {
    try {
      FileDocumentManager.getInstance().saveAllDocuments();

      ConsoleAdapter console = openConsoleToolWindow(myMavenCore.getState());
      myExecutor = createExecutor(parameters, myMavenCore.getState(), settings, console);

      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, myExecutor.getCaption(), true) {
        public void run(@NotNull ProgressIndicator indicator) {
          List<MavenProject> processedProjects = new ArrayList<MavenProject>();

          try {
            if (myExecutor.execute(processedProjects, indicator)) {
              if (onComplete != null) onComplete.run();
            }
          }
          catch (ProcessCanceledException ignore) {
          }

          myExecutor = null;
          myLastRunnerParametersAndSettings = Pair.create(parameters, settings);
          updateProjectFolders(processedProjects);
        }

        @Nullable
        public NotificationInfo getNotificationInfo() {
          return new NotificationInfo("Maven", "Maven Task Finished", "");
        }

        public boolean shouldStartInBackground() {
          return settings.isRunMavenInBackground();
        }

        public void processSentToBackground() {
          settings.setRunMavenInBackground(true);
        }

        public void processRestoredToForeground() {
          settings.setRunMavenInBackground(false);
        }
      });
    }
    catch (Exception e) {
      MavenLog.LOG.error(e);
      Messages.showErrorDialog(myProject, e.getMessage(), "Maven execution error");
    }
  }

  private static Filter[] getFilters(final Project project) {
    return new Filter[]{new ExceptionFilter(project), new RegexpFilter(project, CONSOLE_FILTER_REGEXP)};
  }

  public Pair<MavenRunnerParameters, MavenRunnerSettings> getLatestBuildParametersAndSettings() {
    return myLastRunnerParametersAndSettings;
  }

  public void cancelRun() {
    if (myExecutor != null) {
      myExecutor.cancel();
    }
  }

  public boolean runBatch(List<MavenRunnerParameters> commands,
                          @Nullable MavenCoreSettings coreSettings,
                          @Nullable MavenRunnerSettings runnerSettings,
                          @Nullable final String action,
                          ProgressIndicator indicator) {
    final MavenCoreSettings effectiveCoreSettings = coreSettings != null ? coreSettings : myMavenCore.getState();
    final MavenRunnerSettings effectiveRunnerSettings = runnerSettings != null ? runnerSettings : getState();

    ConsoleAdapter adapter = openConsoleToolWindow(effectiveCoreSettings);

    int count = 0;
    ArrayList<MavenProject> processedProjects = new ArrayList<MavenProject>();
    for (MavenRunnerParameters command : commands) {
      if (indicator != null) {
        indicator.setFraction(((double)count++) / commands.size());
      }

      MavenExecutor executor = createExecutor(command, effectiveCoreSettings, effectiveRunnerSettings, adapter);
      executor.setAction(action);
      if (!executor.execute(processedProjects, indicator)) {
        updateProjectFolders(processedProjects);
        return false;
      }
    }

    updateProjectFolders(processedProjects);
    return true;
  }

  private void updateProjectFolders(List<MavenProject> processedProjects) {
    if (myProject.isDisposed()) return; // project was closed before task finished.
    MavenProjectsManager.getInstance(myProject).updateProjectFolders(processedProjects);
  }

  private ConsoleAdapter openConsoleToolWindow(MavenCoreSettings coreSettings) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new TestConsoleAdapter();
    }
    
    if (myMavenOutputWindowPanel == null) {
      myMavenOutputWindowPanel = new MavenRunnerOutputPanel();
      Disposer.register(myProject, myMavenOutputWindowPanel);
    }

    if (!isToolWindowOpen()) {
      ToolWindowManager.getInstance(myProject)
          .registerToolWindow(OUTPUT_TOOL_WINDOW_ID, myMavenOutputWindowPanel.getRootComponent(), ToolWindowAnchor.BOTTOM).show(null);
    }

    ConsoleView consoleView = createConsoleView();
    myMavenOutputWindowPanel.attachConsole(consoleView);
    return new ConsoleAdapterImpl(consoleView,
                                  coreSettings.getOutputLevel(),
                                  coreSettings.isPrintErrorStackTraces());
  }

  private ConsoleView createConsoleView() {
    TextConsoleBuilderFactory factory = TextConsoleBuilderFactory.getInstance();

    TextConsoleBuilder builder = factory.createBuilder(myProject);

    for (Filter filter : getFilters(myProject)) {
      builder.addFilter(filter);
    }

    return builder.getConsole();
  }

  private MavenExecutor createExecutor(MavenRunnerParameters taskParameters,
                                       MavenCoreSettings coreSettings,
                                       MavenRunnerSettings runnerSettings,
                                       ConsoleAdapter console) {
    if (runnerSettings.isUseMavenEmbedder()) {
      return new MavenEmbeddedExecutor(taskParameters, coreSettings, runnerSettings, console);
    }
    else {
      return new MavenExternalExecutor(taskParameters, coreSettings, runnerSettings, console);
    }
  }
}
