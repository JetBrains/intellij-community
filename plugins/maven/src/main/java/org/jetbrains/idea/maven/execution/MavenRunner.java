// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@State(name = "MavenRunner", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public final class MavenRunner implements PersistentStateComponent<MavenRunnerSettings> {
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

    ProgramRunner.Callback callback = descriptor -> {
      ProcessHandler handler = descriptor.getProcessHandler();
      if (handler == null) return;
      handler.addProcessListener(new ProcessAdapter() {
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
                          @Nullable final String action,
                          @Nullable ProgressIndicator indicator) {
    return runBatch(commands, coreSettings, runnerSettings, action, indicator, null);
  }

  public boolean runBatch(List<MavenRunnerParameters> commands,
                          @Nullable MavenGeneralSettings coreSettings,
                          @Nullable MavenRunnerSettings runnerSettings,
                          @Nullable final String action,
                          @Nullable ProgressIndicator indicator,
                          @Nullable Consumer<? super ProcessHandler> onAttach) {
    return runBatch(commands, coreSettings, runnerSettings, action, indicator, onAttach, false);
  }

  public boolean runBatch(List<MavenRunnerParameters> commands,
                          @Nullable MavenGeneralSettings coreSettings,
                          @Nullable MavenRunnerSettings runnerSettings,
                          @Nullable final String action,
                          @Nullable ProgressIndicator indicator,
                          @Nullable Consumer<? super ProcessHandler> onAttach,
                          boolean isDelegateBuild) {
    if (commands.isEmpty()) return true;
    LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed());

    int count = 0;
    for (MavenRunnerParameters command : commands) {
      if (indicator != null) {
        indicator.setFraction(((double)count++) / commands.size());
        indicator.setText(MessageFormat.format("{0} {1}", action != null ? action : RunnerBundle.message("maven.running"),
                                               command.getWorkingDirPath()));
        indicator.setText2(command.getGoals().toString());
      }
      AtomicReference<ProcessHandler> buildHandler = new AtomicReference<>();
      AtomicReference<ProcessEvent> terminatedEvent = new AtomicReference<>();
      ProgramRunner.Callback callback = descriptor -> {
        buildHandler.set(descriptor.getProcessHandler());
        ProcessHandler handler = descriptor.getProcessHandler();
        if (handler == null) return;
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            terminatedEvent.set(event);
          }
        });
        if (onAttach != null) {
          onAttach.consume(descriptor.getProcessHandler());
        }
      };

      buildHandler.get().waitFor();
      if (terminatedEvent.get().getExitCode() != 0) {
        MavenLog.LOG.error(terminatedEvent.get().getText());
        updateTargetFolders();
        return false;
      }
    }
    updateTargetFolders();
    return true;
  }

  private void updateTargetFolders() {
    if (myProject.isDisposed()) return; // project was closed before task finished.
    MavenProjectsManager.getInstance(myProject).updateProjectTargetFolders();
  }

}
