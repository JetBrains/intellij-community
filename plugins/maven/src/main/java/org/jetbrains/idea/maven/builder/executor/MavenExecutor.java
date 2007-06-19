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


package org.jetbrains.idea.maven.builder.executor;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.idea.maven.builder.BuilderBundle;
import org.jetbrains.idea.maven.builder.MavenBuilderState;
import org.jetbrains.idea.maven.builder.logger.MavenLogUtil;
import org.jetbrains.idea.maven.core.MavenCoreState;

import java.text.MessageFormat;

public abstract class MavenExecutor extends ConsoleAdapter {

  final MavenBuildParameters myParameters;
  final MavenCoreState myCoreState;
  final MavenBuilderState myBuilderState;
  private final String myCaption;
  private String myAction;

  private boolean stopped = true;
  private boolean cancelled = false;
  private int exitCode = 0;

  public MavenExecutor(MavenBuildParameters parameters, MavenCoreState coreState, MavenBuilderState builderState, String caption) {
    super(coreState.getOutputLevel());
    myParameters = parameters;
    myCoreState = coreState;
    myBuilderState = builderState;
    myCaption = caption;
  }

  public String getCaption() {
    return myCaption;
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
    setOutputPaused(false);
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
      indicator.setText(MessageFormat.format("{0} {1}", myAction != null ? myAction : BuilderBundle.message("maven.building"),
                                             FileUtil.toSystemDependentName(myParameters.getPomPath())));
      indicator.setText2(myParameters.getGoals().toString());
    }
  }

  protected void printExitSummary() {
    if (isCancelled()) {
      systemMessage(MavenLogUtil.LEVEL_INFO, BuilderBundle.message("maven.execution.aborted"), null);
    }
    else if (exitCode == 0) {
      systemMessage(MavenLogUtil.LEVEL_INFO, BuilderBundle.message("maven.execution.finished"), null);
    }
    else {
      systemMessage(MavenLogUtil.LEVEL_ERROR, BuilderBundle.message("maven.execution.terminated.abnormally", exitCode), null);
    }
  }

  public abstract void run();
}
