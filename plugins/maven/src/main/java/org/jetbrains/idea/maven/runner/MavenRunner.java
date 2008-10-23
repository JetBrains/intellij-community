package org.jetbrains.idea.maven.runner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.embedder.MavenConsoleHelper;
import org.jetbrains.idea.maven.embedder.MavenConsoleImpl;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.DummyProjectComponent;

import java.util.ArrayList;
import java.util.List;

@State(name = "MavenRunner", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenRunner extends DummyProjectComponent implements PersistentStateComponent<MavenRunnerSettings> {
  private final Project myProject;

  private MavenRunnerSettings mySettings = new MavenRunnerSettings();

  public static MavenRunner getInstance(Project project) {
    return project.getComponent(MavenRunner.class);
  }

  public MavenRunner(final Project project) {
    super("MavenRunner");
    myProject = project;
  }

  public MavenRunnerSettings getState() {
    return mySettings;
  }

  public void loadState(MavenRunnerSettings settings) {
    mySettings = settings;
  }

  public void run(MavenRunnerParameters parameters) {
    run(parameters, mySettings);
  }

  public void run(MavenRunnerParameters parameters, MavenRunnerSettings settings) {
    run(parameters, settings, null);
  }

  public void run(final MavenRunnerParameters parameters, final MavenRunnerSettings settings, final Runnable onComplete) {
    FileDocumentManager.getInstance().saveAllDocuments();

    final MavenConsole console = createConsole(getGeneralSettings(),
                                               Pair.create(parameters, settings));
    try {
      final MavenExecutor[] executor = new MavenExecutor[]{createExecutor(parameters, getGeneralSettings(), settings, console)};

      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, executor[0].getCaption(), true) {
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            List<MavenProject> processedProjects = new ArrayList<MavenProject>();

            try {
              if (executor[0].execute(processedProjects, indicator)) {
                if (onComplete != null) onComplete.run();
              }
            }
            catch (ProcessCanceledException ignore) {
            }

            executor[0] = null;
            updateProjectFolders(processedProjects);
          }
          finally {
            console.finish();
          }
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
      MavenConsoleHelper.printException(console, e);
      console.finish();
      MavenLog.LOG.warn(e);
    }
  }

  private MavenGeneralSettings getGeneralSettings() {
    return MavenProjectsManager.getInstance(myProject).getGeneralSettings();
  }

  public boolean runBatch(List<MavenRunnerParameters> commands,
                          @Nullable MavenGeneralSettings coreSettings,
                          @Nullable MavenRunnerSettings runnerSettings,
                          @Nullable final String action,
                          ProgressIndicator indicator) {
    final MavenGeneralSettings effectiveCoreSettings = coreSettings != null ? coreSettings : getGeneralSettings();
    final MavenRunnerSettings effectiveRunnerSettings = runnerSettings != null ? runnerSettings : getState();

    MavenConsole console = createConsole(effectiveCoreSettings, null);
    try {
      int count = 0;
      ArrayList<MavenProject> processedProjects = new ArrayList<MavenProject>();
      for (MavenRunnerParameters command : commands) {
        if (indicator != null) {
          indicator.setFraction(((double)count++) / commands.size());
        }

        MavenExecutor executor = createExecutor(command, effectiveCoreSettings, effectiveRunnerSettings, console);
        executor.setAction(action);
        if (!executor.execute(processedProjects, indicator)) {
          updateProjectFolders(processedProjects);
          return false;
        }
      }

      updateProjectFolders(processedProjects);
    }
    finally {
      console.finish();
    }

    return true;
  }

  private void updateProjectFolders(List<MavenProject> processedProjects) {
    if (myProject.isDisposed()) return; // project was closed before task finished.
    MavenProjectsManager.getInstance(myProject).updateProjectFolders(processedProjects);
  }

  private MavenConsole createConsole(MavenGeneralSettings coreSettings,
                                     Pair<MavenRunnerParameters, MavenRunnerSettings> parametersAndSettings) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new SoutMavenConsole();
    }
    return new MavenConsoleImpl("Maven Goal", myProject, coreSettings);
  }

  private MavenExecutor createExecutor(MavenRunnerParameters taskParameters,
                                       MavenGeneralSettings coreSettings,
                                       MavenRunnerSettings runnerSettings,
                                       MavenConsole console) {
    return new MavenExternalExecutor(taskParameters, coreSettings, runnerSettings, console);
  }
}
