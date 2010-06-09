/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenConsoleImpl;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.SimpleProjectComponent;

import java.util.List;

@State(name = "MavenRunner", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenRunner extends SimpleProjectComponent implements PersistentStateComponent<MavenRunnerSettings> {
  private MavenRunnerSettings mySettings = new MavenRunnerSettings();

  public static MavenRunner getInstance(Project project) {
    return project.getComponent(MavenRunner.class);
  }

  public MavenRunner(final Project project) {
    super(project);
  }

  public MavenRunnerSettings getSettings() {
    return mySettings;
  }

  public MavenRunnerSettings getState() {
    return mySettings;
  }

  public void loadState(MavenRunnerSettings settings) {
    mySettings = settings;
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

  private MavenGeneralSettings getGeneralSettings() {
    return MavenProjectsManager.getInstance(myProject).getGeneralSettings();
  }

  public boolean runBatch(List<MavenRunnerParameters> commands,
                          @Nullable MavenGeneralSettings coreSettings,
                          @Nullable MavenRunnerSettings runnerSettings,
                          @Nullable final String action,
                          ProgressIndicator indicator) {
    if (commands.isEmpty()) return true;
    
    final MavenGeneralSettings effectiveCoreSettings = coreSettings != null ? coreSettings : getGeneralSettings();
    final MavenRunnerSettings effectiveRunnerSettings = runnerSettings != null ? runnerSettings : getState();

    MavenConsole console = createConsole(effectiveCoreSettings, null);
    try {
      int count = 0;
      for (MavenRunnerParameters command : commands) {
        if (indicator != null) {
          indicator.setFraction(((double)count++) / commands.size());
        }

        MavenExecutor executor = createExecutor(command, effectiveCoreSettings, effectiveRunnerSettings, console);
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

  private MavenConsole createConsole(MavenGeneralSettings coreSettings,
                                     Pair<MavenRunnerParameters, MavenRunnerSettings> parametersAndSettings) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new SoutMavenConsole();
    }
    return new MavenConsoleImpl("Maven Goal", myProject);
  }

  private MavenExecutor createExecutor(MavenRunnerParameters taskParameters,
                                       MavenGeneralSettings coreSettings,
                                       MavenRunnerSettings runnerSettings,
                                       MavenConsole console) {
    return new MavenExternalExecutor(taskParameters, coreSettings, runnerSettings, console);
  }
}
