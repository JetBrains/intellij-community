// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.List;

@State(name = "MavenRunner", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class MavenRunner implements PersistentStateComponent<MavenRunnerSettings> {

  private static final Logger LOG = Logger.getInstance(MavenRunner.class);

  private MavenRunnerSettings mySettings = new MavenRunnerSettings();
  private final Project myProject;

  public static MavenRunner getInstance(Project project) {
    return ServiceManager.getService(project, MavenRunner.class);
  }

  public MavenRunner(final Project project) {
    myProject = project;
  }

  public MavenRunnerSettings getSettings() {
    return mySettings;
  }

  @Override
  @NotNull
  public MavenRunnerSettings getState() {
    return mySettings;
  }

  @Override
  public void loadState(@NotNull MavenRunnerSettings settings) {
    mySettings = settings;
  }

  public void run(final MavenRunnerParameters parameters, final MavenRunnerSettings settings, final Runnable onComplete) {
    FileDocumentManager.getInstance().saveAllDocuments();

    final MavenConsole console = createConsole(myProject, parameters.getWorkingDirPath(), StringUtil.join(parameters.getGoals(), ", "), 0);
    try {
      final MavenExecutor executor = createExecutor(parameters, null, settings, console);

      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, executor.getCaption(), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            try {
              if (executor.execute(indicator)) {
                if (onComplete != null) onComplete.run();
              }
            }
            catch (ProcessCanceledException ignore) {
            }

            updateTargetFolders();
          }
          finally {
            console.finish();
          }
        }

        @Override
        @Nullable
        public NotificationInfo getNotificationInfo() {
          return new NotificationInfo("Maven", "Maven Task Finished", "");
        }

        @Override
        public boolean shouldStartInBackground() {
          return settings.isRunMavenInBackground();
        }

        @Override
        public void processSentToBackground() {
          settings.setRunMavenInBackground(true);
        }

        public void processRestoredToForeground() {
          settings.setRunMavenInBackground(false);
        }
      });
    }
    catch (Exception e) {
      console.printException(e);
      console.finish();
      MavenLog.LOG.warn(e);
    }
  }

  public boolean runBatch(List<MavenRunnerParameters> commands,
                          @Nullable MavenGeneralSettings coreSettings,
                          @Nullable MavenRunnerSettings runnerSettings,
                          @Nullable final String action,
                          @Nullable ProgressIndicator indicator) {

    return runBatch(commands, coreSettings, runnerSettings, action, indicator, createConsole(myProject, myProject.getBasePath(), "Maven Batch", 0));
  }

  public boolean runBatch(List<MavenRunnerParameters> commands,
                          @Nullable MavenGeneralSettings coreSettings,
                          @Nullable MavenRunnerSettings runnerSettings,
                          @Nullable final String action,
                          @Nullable ProgressIndicator indicator,
                          @NotNull MavenConsole mavenConsole) {
    LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed());

    if (commands.isEmpty()) return true;

    try {
      int count = 0;
      for (MavenRunnerParameters command : commands) {
        if (indicator != null) {
          indicator.setFraction(((double)count++) / commands.size());
        }

        MavenExecutor executor = ReadAction.compute(() -> {
          if (myProject.isDisposed()) return null;
          return createExecutor(command, coreSettings, runnerSettings, mavenConsole);
        });
        if (executor == null) break;

        executor.setAction(action);
        if (!executor.execute(indicator)) {
          updateTargetFolders();
          return false;
        }
      }

      updateTargetFolders();
    }
    finally {
      mavenConsole.finish();
    }

    return true;
  }

  public static MavenConsole createConsole(@NotNull Project project,
                                           @NotNull String workingDirPath,
                                           @NotNull String title,
                                           long executionId) {
    return ReadAction.compute(() -> {
      if (project.isDisposed()) return null;
      return doCreateConsole(title, workingDirPath, project, executionId);
    });
  }

  private void updateTargetFolders() {
    if (myProject.isDisposed()) return; // project was closed before task finished.
    MavenProjectsManager.getInstance(myProject).updateProjectTargetFolders();
  }

  private static MavenConsole doCreateConsole(@NotNull String title, @NotNull String workingDirPath, Project project, long executionId) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new SoutMavenConsole();
    }
    return MavenConsole.createGuiMavenConsole(project, title, workingDirPath, ToolWindowId.RUN, executionId);
  }

  private MavenExecutor createExecutor(MavenRunnerParameters taskParameters,
                                       @Nullable MavenGeneralSettings coreSettings,
                                       @Nullable MavenRunnerSettings runnerSettings,
                                       @NotNull MavenConsole console) {
    return new MavenExternalExecutor(myProject, taskParameters, coreSettings, runnerSettings, console);
  }
}
