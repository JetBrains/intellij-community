package org.jetbrains.idea.maven.runner;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;
import org.jetbrains.idea.maven.core.util.ErrorHandler;
import org.jetbrains.idea.maven.runner.executor.MavenEmbeddedExecutor;
import org.jetbrains.idea.maven.runner.executor.MavenExecutor;
import org.jetbrains.idea.maven.runner.executor.MavenExternalExecutor;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;

import java.util.List;

@State(name = "MavenRunner", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenRunnerImpl extends DummyProjectComponent implements MavenRunner, PersistentStateComponent<MavenRunnerSettings> {

  @NonNls private static final String OUTPUT_TOOL_WINDOW_ID = "Maven Runner Output";

  private final Project project;
  private final MavenCore mavenCore;

  private MavenRunnerParameters myRunnerParameters;

  private MavenRunnerSettings mySettings = new MavenRunnerSettings();

  private MavenRunnerOutputPanel myMavenOutputWindowPanel;
  private MavenExecutor executor;

  public MavenRunnerImpl(final Project project, MavenCore mavenCore) {
    super("MavenRunner");

    this.project = project;
    this.mavenCore = mavenCore;

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
    return executor;
  }

  public boolean isToolWindowOpen() {
    return ToolWindowManager.getInstance(project).getToolWindow(OUTPUT_TOOL_WINDOW_ID) != null;
  }

  void openToolWindow(final ConsoleView consoleView) {
    if (myMavenOutputWindowPanel == null) {
      myMavenOutputWindowPanel = new MavenRunnerOutputPanel();
    }

    if (!isToolWindowOpen()) {
      ToolWindowManager.getInstance(project)
        .registerToolWindow(OUTPUT_TOOL_WINDOW_ID, myMavenOutputWindowPanel.getRootComponent(), ToolWindowAnchor.BOTTOM)
        .show(null);
    }

    myMavenOutputWindowPanel.attachConsole(consoleView);
  }

  public void closeToolWindow() {
    if (isToolWindowOpen()) {
      ToolWindowManager.getInstance(project).unregisterToolWindow(OUTPUT_TOOL_WINDOW_ID);
    }
  }

  public boolean isRunning() {
    return executor != null && !executor.isStopped();
  }

  public void run(final MavenRunnerParameters parameters) {
    myRunnerParameters = parameters;
    try {
      FileDocumentManager.getInstance().saveAllDocuments();

      executor = createTask(myRunnerParameters, mavenCore.getState(), mySettings);
      openToolWindow(executor.createConsole(project));
      ProgressManager.getInstance().run(new Task.Backgroundable(project, executor.getCaption(), true) {
        public void run(ProgressIndicator indicator) {
          executor.execute();
        }

        @Nullable
        public NotificationInfo getNotificationInfo() {
          return new NotificationInfo("Maven",  "Maven Task Finished", "");
        }

        public void onSuccess() {
          onRunComplete();
        }

        public void onCancel() {
          onRunComplete();
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
      ErrorHandler.showError(project, e, false);
    }
  }

  private void onRunComplete() {
    executor = null;
    if (mySettings.isSyncAfterBuild()) {
      VirtualFileManager.getInstance().refresh(false);
    }
  }

  public MavenRunnerParameters getLatestBuildParameters() {
    return myRunnerParameters;
  }

  public void cancelRun() {
    if (executor != null) {
      executor.cancel();
    }
  }

  public boolean runBatch(List<MavenRunnerParameters> commands,
                          @Nullable MavenCoreSettings coreSettings,
                          @Nullable MavenRunnerSettings runnerSettings,
                          @Nullable final String action) {
    final MavenCoreSettings effectiveCoreSettings = coreSettings != null ? coreSettings : mavenCore.getState();
    final MavenRunnerSettings effectiveRunnerSettings = runnerSettings != null ? runnerSettings : getState();

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    int count = 0;
    for (MavenRunnerParameters command : commands) {
      if (indicator != null) {
        indicator.setFraction(((double)count++) / commands.size());
      }

      final MavenExecutor task = createTask(command, effectiveCoreSettings, effectiveRunnerSettings);
      task.setAction(action);
      if (!task.execute()) {
        return false;
      }
    }
    return true;
  }

  static MavenExecutor createTask(final MavenRunnerParameters taskParameters,
                                  final MavenCoreSettings coreSettings,
                                  final MavenRunnerSettings runnerSettings) {
    if (runnerSettings.isUseMavenEmbedder()) {
      return new MavenEmbeddedExecutor(taskParameters, coreSettings, runnerSettings);
    }
    else {
      return new MavenExternalExecutor(taskParameters, coreSettings, runnerSettings);
    }
  }
}
