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
import org.jetbrains.idea.maven.builder.logger.LogListener;
import org.jetbrains.idea.maven.builder.logger.MavenBuildLogger;
import org.jetbrains.idea.maven.core.MavenCoreState;

import java.util.List;

public abstract class MavenExecutor implements Runnable {
  void displayProgress() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(BuilderBundle.message("maven.building", FileUtil.toSystemDependentName(parameters.getPomFile())));
      indicator.setText2(parameters.getGoals().toString());
    }
  }

  public interface Parameters {

    public List<String> getGoals();

    public String getPomFile();

    public String getWorkingDir();
  }

  final Parameters parameters;
  final MavenCoreState myMavenCoreState;
  final MavenBuilderState myBuilderState;

  private boolean stopped = true;
  private boolean cancelled = false;

  public MavenExecutor(Parameters parameters, MavenCoreState mavenCoreState, MavenBuilderState builderState) {
    this.parameters = parameters;
    this.myMavenCoreState = mavenCoreState;
    this.myBuilderState = builderState;
  }

  public boolean isStopped() {
    return stopped;
  }

  void start() {
    stopped = false;
    getLogger().setOutputType(LogListener.OUTPUT_TYPE_NORMAL);
  }

  int stop() {
    stopped = true;
    getLogger().setOutputPaused(false);
    return 0;
  }

  boolean isCancelled() {
    return cancelled;
  }

  public void cancel() {
    this.cancelled = true;
    stop();
  }

  public abstract String getCaption();

  public abstract MavenBuildLogger getLogger();
}
