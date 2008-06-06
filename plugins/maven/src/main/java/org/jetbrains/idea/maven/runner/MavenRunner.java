package org.jetbrains.idea.maven.runner;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;
import org.jetbrains.idea.maven.core.util.ErrorHandler;
import org.jetbrains.idea.maven.runner.executor.MavenEmbeddedExecutor;
import org.jetbrains.idea.maven.runner.executor.MavenExecutor;
import org.jetbrains.idea.maven.runner.executor.MavenExternalExecutor;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.util.ArrayList;
import java.util.List;

@State(name = "MavenRunner", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenRunner extends DummyProjectComponent implements PersistentStateComponent<MavenRunnerSettings> {
  @NonNls private static final String OUTPUT_TOOL_WINDOW_ID = "Maven Runner Output";

  private final Project myProject;
  private final MavenCore myMavenCore;

  private MavenRunnerSettings mySettings = new MavenRunnerSettings();
  private MavenRunnerParameters myRunnerParameters;

  private MavenExecutor myExecutor;
  private MavenRunnerOutputPanel myMavenOutputWindowPanel;

  private ArrayList<MavenProject> myProcessedProjects;

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

  void openToolWindow(final ConsoleView consoleView) {
    if (myMavenOutputWindowPanel == null) {
      myMavenOutputWindowPanel = new MavenRunnerOutputPanel();
    }

    if (!isToolWindowOpen()) {
      ToolWindowManager.getInstance(myProject)
          .registerToolWindow(OUTPUT_TOOL_WINDOW_ID, myMavenOutputWindowPanel.getRootComponent(), ToolWindowAnchor.BOTTOM)
          .show(null);
    }

    myMavenOutputWindowPanel.attachConsole(consoleView);
  }

  public void closeToolWindow() {
    if (isToolWindowOpen()) {
      ToolWindowManager.getInstance(myProject).unregisterToolWindow(OUTPUT_TOOL_WINDOW_ID);
    }
  }

  public boolean isRunning() {
    return myExecutor != null && !myExecutor.isStopped();
  }

  public void run(final MavenRunnerParameters parameters) {
    myRunnerParameters = parameters;
    try {
      FileDocumentManager.getInstance().saveAllDocuments();

      myProcessedProjects = new ArrayList<MavenProject>();
      myExecutor = createExecutor(myRunnerParameters, myMavenCore.getState(), mySettings);

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        openToolWindow(myExecutor.createConsole(myProject));
      }
      
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, myExecutor.getCaption(), true) {
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            myExecutor.execute(myProcessedProjects, indicator);
          }
          catch (ProcessCanceledException e) {
          }
          onRunComplete();
        }

        @Nullable
        public NotificationInfo getNotificationInfo() {
          return new NotificationInfo("Maven", "Maven Task Finished", "");
        }

        public boolean shouldStartInBackground() {
          return mySettings.isRunMavenInBackground();
        }

        public void processSentToBackground() {
          mySettings.setRunMavenInBackground(true);
        }

        public void processRestoredToForeground() {
          mySettings.setRunMavenInBackground(false);
        }
      });
    }
    catch (Exception e) {
      ErrorHandler.showError(myProject, e, false);
    }
  }

  private void onRunComplete() {
    myExecutor = null;
    updateProjectFolders(myProcessedProjects);
  }

  public MavenRunnerParameters getLatestBuildParameters() {
    return myRunnerParameters;
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

    int count = 0;
    ArrayList<MavenProject> processedProjects = new ArrayList<MavenProject>();
    for (MavenRunnerParameters command : commands) {
      if (indicator != null) {
        indicator.setFraction(((double)count++) / commands.size());
      }

      MavenExecutor executor = createExecutor(command, effectiveCoreSettings, effectiveRunnerSettings);
      executor.setAction(action);
      if (!executor.execute(processedProjects, indicator)) {
        updateProjectFolders(processedProjects);
        return false;
      }
    }

    updateProjectFolders(processedProjects);
    return true;
  }

  private static MavenExecutor createExecutor(MavenRunnerParameters taskParameters,
                                              MavenCoreSettings coreSettings,
                                              MavenRunnerSettings runnerSettings) {
    if (runnerSettings.isUseMavenEmbedder()) {
      return new MavenEmbeddedExecutor(taskParameters, coreSettings, runnerSettings);
    }
    else {
      return new MavenExternalExecutor(taskParameters, coreSettings, runnerSettings);
    }
  }

  private void updateProjectFolders(ArrayList<MavenProject> processedProjects) {
    MavenProjectsManager.getInstance(myProject).updateProjectFolders(processedProjects);
  }
}
