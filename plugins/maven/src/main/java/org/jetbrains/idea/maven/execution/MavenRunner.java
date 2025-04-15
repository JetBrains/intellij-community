// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.List;

@State(name = "MavenRunner", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@Service(Service.Level.PROJECT)
public final class MavenRunner implements PersistentStateComponent<MavenRunnerSettings> {
  private MavenRunnerSettings mySettings = new MavenRunnerSettings();
  private final Project myProject;

  public static MavenRunner getInstance(Project project) {
    return project.getService(MavenRunner.class);
  }

  public static @Nullable MavenRunner getInstanceIfCreated(@NotNull Project project) {
    return project.getServiceIfCreated(MavenRunner.class);
  }

  public MavenRunner(final Project project) {
    myProject = project;
  }

  public MavenRunnerSettings getSettings() {
    return mySettings;
  }

  @Override
  public @NotNull MavenRunnerSettings getState() {
    return mySettings;
  }

  @Override
  public void loadState(@NotNull MavenRunnerSettings settings) {
    mySettings = settings;
  }

  public void run(final MavenRunnerParameters parameters, final MavenRunnerSettings settings, final Runnable onComplete) {
    ApplicationManager.getApplication().invokeAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());

    ProgramRunner.Callback callback = descriptor -> {
      ProcessHandler handler = descriptor.getProcessHandler();
      if (handler == null) return;
      handler.addProcessListener(new ProcessListener() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          if (event.getExitCode() == 0 && onComplete != null) {
            onComplete.run();
          }
        }
      });
    };

    MavenRunConfigurationType.runConfiguration(myProject, parameters, null, settings, callback, false);
  }

  public boolean runBatch(List<MavenRunnerParameters> commands,
                          @Nullable MavenGeneralSettings coreSettings,
                          @Nullable MavenRunnerSettings runnerSettings,
                          final @Nullable String action,
                          @Nullable ProgressIndicator indicator) {
    return runBatch(commands, coreSettings, runnerSettings, action, indicator, null);
  }

  public boolean runBatch(List<MavenRunnerParameters> commands,
                          @Nullable MavenGeneralSettings coreSettings,
                          @Nullable MavenRunnerSettings runnerSettings,
                          final @Nullable String action,
                          @Nullable ProgressIndicator indicator,
                          @Nullable Consumer<? super ProcessHandler> onAttach) {
    return runBatch(commands, coreSettings, runnerSettings, action, indicator, onAttach, false);
  }

  public boolean runBatch(List<MavenRunnerParameters> commands,
                          @Nullable MavenGeneralSettings coreSettings,
                          @Nullable MavenRunnerSettings runnerSettings,
                          final @Nullable String action,
                          @Nullable ProgressIndicator indicator,
                          @Nullable Consumer<? super ProcessHandler> onAttach,
                          boolean isDelegateBuild) {
    if (commands.isEmpty()) return true;

    int count = 0;
    for (MavenRunnerParameters command : commands) {
      if (indicator != null) {
        indicator.setFraction(((double)count++) / commands.size());
        indicator.setText(RunnerBundle.message("maven.running", action != null ? action : command.getWorkingDirPath()));
        indicator.setText2(command.getGoals().toString()); //NON-NLS
      }
      ProgramRunner.Callback callback = descriptor -> {
        ProcessHandler handler = descriptor.getProcessHandler();
        if (handler != null) {
          handler.addProcessListener(new ProcessListener() {
            @Override
            public void startNotified(@NotNull ProcessEvent event) {
              if (onAttach != null) {
                onAttach.consume(handler);
              }
            }

            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
              updateTargetFolders();
            }
          });
        }
      };
      MavenRunConfigurationType.runConfiguration(myProject, command, null, null, callback, isDelegateBuild);
    }
    return true;
  }

  private void updateTargetFolders() {
    if (myProject.isDisposed()) return; // project was closed before task finished.
    MavenProjectsManager.getInstance(myProject).updateProjectTargetFolders();
  }
}
