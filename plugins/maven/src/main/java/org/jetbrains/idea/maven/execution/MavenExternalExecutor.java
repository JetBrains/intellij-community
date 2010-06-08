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


package org.jetbrains.idea.maven.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.DefaultJavaProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.facade.MavenFacadeConsole;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;

public class MavenExternalExecutor extends MavenExecutor {

  private OSProcessHandler myProcessHandler;

  @NonNls private static final String PHASE_INFO_REGEXP = "\\[INFO\\] \\[.*:.*\\]";
  @NonNls private static final int INFO_PREFIX_SIZE = "[INFO] ".length();

  public MavenExternalExecutor(MavenRunnerParameters parameters,
                               MavenGeneralSettings coreSettings,
                               MavenRunnerSettings runnerSettings,
                               MavenConsole console) {
    super(parameters, coreSettings, runnerSettings, RunnerBundle.message("external.executor.caption"), console);
  }

  public boolean execute(final ProgressIndicator indicator) {
    displayProgress();

    try {
      myProcessHandler =
          new DefaultJavaProcessHandler(MavenExternalParameters.createJavaParameters(myParameters, myCoreSettings, myRunnerSettings)) {
            public void notifyTextAvailable(String text, Key outputType) {
              // todo move this logic to ConsoleAdapter class
              if (!myConsole.isSuppressed(text)) {
                super.notifyTextAvailable(text, outputType);
              }
              updateProgress(indicator, text);
            }
          };

      myConsole.attachToProcess(myProcessHandler);
    }
    catch (ExecutionException e) {
      myConsole.systemMessage(MavenFacadeConsole.LEVEL_FATAL, RunnerBundle.message("external.startup.failed", e.getMessage()), null);
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
