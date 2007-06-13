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
import org.jetbrains.idea.maven.builder.MavenBuilderState;
import org.jetbrains.idea.maven.builder.logger.ConsoleOutputAdapter;
import org.jetbrains.idea.maven.builder.logger.LogListener;
import org.jetbrains.idea.maven.builder.logger.MavenBuildLogger;
import org.jetbrains.idea.maven.core.MavenCoreState;

import java.util.List;

public abstract class MavenExecutor {

  public interface Parameters {

    List<String> getGoals();

    String getPomFile();

    String getWorkingDir();

    String getCaption();
  }

  final Parameters myParameters;
  final MavenCoreState myMavenCoreState;
  final MavenBuilderState myBuilderState;

  protected ConsoleOutputAdapter consoleOutput;
  protected MavenBuildLogger buildLogger;

  private boolean stopped = true;
  private boolean cancelled = false;

  public MavenExecutor(Parameters parameters, MavenCoreState mavenCoreState, MavenBuilderState builderState) {
    myParameters = parameters;
    myMavenCoreState = mavenCoreState;
    myBuilderState = builderState;
  }

  public boolean isStopped() {
    return stopped;
  }

  void start() {
    stopped = false;
    buildLogger.setOutputType(LogListener.OUTPUT_TYPE_NORMAL);
  }

  int stop() {
    stopped = true;
    buildLogger.setOutputPaused(false);
    return 0;
  }

  boolean isCancelled() {
    return cancelled;
  }

  public void cancel() {
    cancelled = true;
    stop();
  }

  public void run(LogListener listener) {

    buildLogger = createLogger();
    buildLogger.setThreshold(myMavenCoreState.getOutputLevel());
    if (listener != null) {
      buildLogger.addListener(listener);
    }
    consoleOutput = new ConsoleOutputAdapter(buildLogger);

    displayProgress();

    doRun();

    dispose ();
  }

  protected abstract MavenBuildLogger createLogger();

  void displayProgress() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(myParameters.getCaption());
      indicator.setText2(myParameters.getGoals().toString());
    }
  }

  protected abstract void doRun();

  protected void dispose() {
    buildLogger = null;
    consoleOutput = null;
  }

  public abstract String getCaption();

  public MavenBuildLogger getLogger() {
    return buildLogger;
  }
}
