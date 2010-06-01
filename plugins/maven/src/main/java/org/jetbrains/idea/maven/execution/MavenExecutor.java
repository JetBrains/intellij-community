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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.idea.maven.facade.MavenFacadeConsole;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;

import java.text.MessageFormat;

public abstract class MavenExecutor  {
  final MavenRunnerParameters myParameters;
  final MavenGeneralSettings myCoreSettings;
  final MavenRunnerSettings myRunnerSettings;
  private final String myCaption;
  protected MavenConsole myConsole;
  private String myAction;

  private boolean stopped = true;
  private boolean cancelled = false;
  private int exitCode = 0;

  public MavenExecutor(MavenRunnerParameters parameters,
                       MavenGeneralSettings coreSettings,
                       MavenRunnerSettings runnerSettings,
                       String caption,
                       MavenConsole console) {
    myParameters = parameters;
    myCoreSettings = coreSettings;
    myRunnerSettings = runnerSettings;
    myCaption = caption;
    myConsole = console;
  }

  public String getCaption() {
    return myCaption;
  }

  public MavenConsole getConsole() {
    return myConsole;
  }

  public void setAction(final String action) {
    myAction = action;
  }

  public boolean isStopped() {
    return stopped;
  }

  void start() {
    stopped = false;
  }

  void stop() {
    stopped = true;
    myConsole.setOutputPaused(false);
  }

  boolean isCancelled() {
    return cancelled;
  }

  public void cancel() {
    cancelled = true;
    stop();
  }

  protected void setExitCode(int exitCode) {
    this.exitCode = exitCode;
  }

  void displayProgress() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(MessageFormat.format("{0} {1}", myAction != null ? myAction : RunnerBundle.message("maven.running"),
                                             myParameters.getWorkingDirPath()));
      indicator.setText2(myParameters.getGoals().toString());
    }
  }

  protected boolean printExitSummary() {
    if (isCancelled()) {
      myConsole.systemMessage(MavenFacadeConsole.LEVEL_INFO, RunnerBundle.message("maven.execution.aborted"), null);
      return false;
    }
    else if (exitCode == 0) {
      myConsole.systemMessage(MavenFacadeConsole.LEVEL_INFO, RunnerBundle.message("maven.execution.finished"), null);
      return true;
    }
    else {
      myConsole.systemMessage(MavenFacadeConsole.LEVEL_ERROR, RunnerBundle.message("maven.execution.terminated.abnormally", exitCode), null);
      return false;
    }
  }

  public abstract boolean execute(ProgressIndicator indicator);
}
