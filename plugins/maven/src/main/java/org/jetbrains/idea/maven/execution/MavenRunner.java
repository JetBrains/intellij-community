/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.application.AccessToken;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenConsoleImpl;
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

  @NotNull
  public MavenRunnerSettings getState() {
    return mySettings;
  }

  public void loadState(MavenRunnerSettings settings) {
    mySettings = settings;
  }

  public void run(final MavenRunnerParameters parameters, final MavenRunnerSettings settings, final Runnable onComplete) {
    FileDocumentManager.getInstance().saveAllDocuments();

    final MavenConsole console = createConsole();
    try {
      final MavenExecutor[] executor = new MavenExecutor[]{createExecutor(parameters, null, settings, console)};

      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, executor[0].getCaption(), true) {
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            try {
              if (executor[0].execute(indicator)) {
                if (onComplete != null) onComplete.run();
              }
            }
            catch (ProcessCanceledException ignore) {
            }

            executor[0] = null;
            updateTargetFolders();
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
    LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed());

    if (commands.isEmpty()) return true;

    MavenConsole console;

    AccessToken accessToken = ReadAction.start();
    try {
      if (myProject.isDisposed()) return false;
      console = createConsole();
    }
    finally {
      accessToken.finish();
    }

    try {
      int count = 0;
      for (MavenRunnerParameters command : commands) {
        if (indicator != null) {
          indicator.setFraction(((double)count++) / commands.size());
        }

        MavenExecutor executor;

        accessToken = ReadAction.start();
        try {
          if (myProject.isDisposed()) break;
          executor = createExecutor(command, coreSettings, runnerSettings, console);
        }
        finally {
          accessToken.finish();
        }

        executor.setAction(action);
        if (!executor.execute(indicator)) {
          updateTargetFolders();
          return false;
        }
      }

      updateTargetFolders();
    }
    finally {
      console.finish();
    }

    return true;
  }

  private void updateTargetFolders() {
    if (myProject.isDisposed()) return; // project was closed before task finished.
    MavenProjectsManager.getInstance(myProject).updateProjectTargetFolders();
  }

  private MavenConsole createConsole() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new SoutMavenConsole();
    }
    return new MavenConsoleImpl("Maven Goal", myProject);
  }

  private MavenExecutor createExecutor(MavenRunnerParameters taskParameters,
                                       @Nullable MavenGeneralSettings coreSettings,
                                       @Nullable MavenRunnerSettings runnerSettings,
                                       MavenConsole console) {
    return new MavenExternalExecutor(myProject, taskParameters, coreSettings, runnerSettings, console);
  }
}
