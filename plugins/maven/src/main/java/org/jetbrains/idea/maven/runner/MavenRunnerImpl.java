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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;
import org.jetbrains.idea.maven.runner.executor.MavenEmbeddedExecutor;
import org.jetbrains.idea.maven.runner.executor.MavenExecutor;
import org.jetbrains.idea.maven.runner.executor.MavenExternalExecutor;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreState;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;
import org.jetbrains.idea.maven.core.util.ErrorHandler;

import java.util.List;

@State(name = "MavenRunner", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenRunnerImpl extends DummyProjectComponent implements MavenRunner, PersistentStateComponent<MavenRunnerState> {

  @NonNls private static final String OUTPUT_TOOL_WINDOW_ID = "Maven Runner Output";

  private final Project project;
  private final MavenCore mavenCore;

  private MavenRunnerParameters myRunnerParameters;

  private MavenRunnerState myState = new MavenRunnerState();

  private MavenRunnerOutputPanel myMavenOutputWindowPanel;
  private MavenExecutor executor;

  public MavenRunnerImpl(final Project project, MavenCore mavenCore) {
    super("MavenRunner");

    this.project = project;
    this.mavenCore = mavenCore;

    mavenCore.addConfigurableFactory(new MavenCore.ConfigurableFactory() {
      public Configurable createConfigurable() {
        return new MavenRunnerConfigurable(project, false) {
          protected MavenRunnerState getState() {
            return myState;
          }
        };
      }
    });
  }

  public MavenRunnerState getState() {
    return myState;
  }

  public void loadState(MavenRunnerState state) {
    myState = state;
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
      Disposer.register(project, myMavenOutputWindowPanel);
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

      executor = createTask(myRunnerParameters, mavenCore.getState(), myState);
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
          return myState.isRunMavenInBackground();
        }

        public void processSentToBackground() {
          myState.setRunMavenInBackground(true);
        }

        public void processRestoredToForeground() {
          myState.setRunMavenInBackground(false);
        }
      });
    }
    catch (Exception e) {
      ErrorHandler.showError(project, e, false);
    }
  }

  private void onRunComplete() {
    executor = null;
    if (myState.isSyncAfterBuild()) {
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
                          @Nullable MavenCoreState coreState,
                          @Nullable MavenRunnerState runnerState,
                          @Nullable final String action) {
    final MavenCoreState effectiveCoreState = coreState != null ? coreState : mavenCore.getState();
    final MavenRunnerState effectiveRunnerState = runnerState != null ? runnerState : getState();

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    int count = 0;
    for (MavenRunnerParameters command : commands) {
      if (indicator != null) {
        indicator.setFraction(((double)count++) / commands.size());
      }

      final MavenExecutor task = createTask(command, effectiveCoreState, effectiveRunnerState);
      task.setAction(action);
      if (!task.execute()) {
        return false;
      }
    }
    return true;
  }

  static MavenExecutor createTask(final MavenRunnerParameters taskParameters,
                                  final MavenCoreState state,
                                  final MavenRunnerState runnerState) {
    if (runnerState.isUseMavenEmbedder()) {
      return new MavenEmbeddedExecutor(taskParameters, state, runnerState);
    }
    else {
      return new MavenExternalExecutor(taskParameters, state, runnerState);
    }
  }
}
