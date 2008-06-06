/* ==========================================================================
 * Copyright 2006 Mevenide Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */


package org.jetbrains.idea.maven.runner.executor;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.DefaultJavaProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.runner.MavenRunnerSettings;
import org.jetbrains.idea.maven.runner.RunnerBundle;
import org.jetbrains.idea.maven.runner.logger.MavenLogUtil;
import org.apache.maven.project.MavenProject;

import java.util.List;

public class MavenExternalExecutor extends MavenExecutor {

  private OSProcessHandler myProcessHandler;

  @NonNls private static final String PHASE_INFO_REGEXP = "\\[INFO\\] \\[.*:.*\\]";
  @NonNls private static final int INFO_PREFIX_SIZE = "[INFO] ".length();

  public MavenExternalExecutor(MavenRunnerParameters parameters, MavenCoreSettings coreSettings, MavenRunnerSettings runnerSettings) {
    super(parameters, coreSettings, runnerSettings, RunnerBundle.message("external.executor.caption"));
  }

  public boolean execute(List<MavenProject> processedProjects, ProgressIndicator indicator) {
    displayProgress();

    try {

      myProcessHandler = new DefaultJavaProcessHandler(MavenExternalParameters.createJavaParameters(myParameters, myCoreSettings, myRunnerSettings)) {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

        public void notifyTextAvailable(String text, Key outputType) {
          if (isNotSuppressed(MavenLogUtil.getLevel(text))) {
            super.notifyTextAvailable(text, outputType);
          }
          updateProgress(indicator, text);
        }
      };

      attachToProcess(myProcessHandler);
    }
    catch (ExecutionException e) {
      systemMessage(MavenLogUtil.LEVEL_FATAL, RunnerBundle.message("external.startup.failed", e.getMessage()), null);
      return false;
    }

    start();
    readProcessOutput();
    stop();

    return printExitSummary();
  }

  void stop() {
    if (myProcessHandler != null) {
      myProcessHandler.destroyProcess();
      myProcessHandler.waitFor();
      setExitCode(myProcessHandler.getProcess().exitValue());
    }
    super.stop();
  }

  private void readProcessOutput() {
    myProcessHandler.startNotify();
    myProcessHandler.waitFor();
  }

  private void updateProgress(final ProgressIndicator indicator, final String text) {
    if (indicator != null) {
      if (indicator.isCanceled()) {
        if (!isCancelled()) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              cancel();
            }
          });
        }
      }
      if (text.matches(PHASE_INFO_REGEXP)) {
        indicator.setText2(text.substring(INFO_PREFIX_SIZE));
      }
    }
  }
}
