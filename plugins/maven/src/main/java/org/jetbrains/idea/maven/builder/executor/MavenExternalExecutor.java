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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.builder.BuilderBundle;
import org.jetbrains.idea.maven.builder.MavenBuilderState;
import org.jetbrains.idea.maven.builder.logger.*;
import org.jetbrains.idea.maven.core.MavenCoreState;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class MavenExternalExecutor extends MavenExecutor {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.builder.executor.MavenExternalExecutor");

  private Process mavenProcess;

  @NonNls private static final String INFO_REGEXP = "\\[INFO\\] \\[.*:.*\\]";
  @NonNls private static final int INFO_PREFIX_SIZE = "[INFO] ".length();

  private static class MavenExternalLogger extends LogBroadcaster implements MavenBuildLogger {
  }

  public MavenExternalExecutor(Parameters parameters, MavenCoreState mavenCoreState, MavenBuilderState builderState) {
    super(parameters, mavenCoreState, builderState);
  }

  protected MavenBuildLogger createLogger (){
    return new MavenExternalLogger();
  }

  public void doRun() {

    List<String> executionCommand;
    try {
      executionCommand = MavenExternalParameters.createCommand(myParameters, myBuilderState, myMavenCoreState);
      logCommand(executionCommand);
    }
    catch (MavenExternalParameters.MavenConfigErrorException e) {
      LOG.error(e.getMessage(), e);
      consoleOutput.message(MavenBuildLogger.LEVEL_FATAL, BuilderBundle.message("external.config.error") + e.getMessage(), e);
      return;
    }

    try {
      mavenProcess = Runtime.getRuntime()
        .exec(executionCommand.toArray(new String[executionCommand.size()]), null, new File(myParameters.getWorkingDir()));
    }
    catch (IOException e) {
      LOG.error(e.getMessage(), e);
      consoleOutput.message(MavenBuildLogger.LEVEL_ERROR, BuilderBundle.message("external.statup.failed"), e);
      return;
    }

    start();
    readProcessOutput();
    int exitValue = stop();

    if (isCancelled()) {
      consoleOutput.message(MavenBuildLogger.LEVEL_INFO, BuilderBundle.message("external.process.aborted", exitValue), null);
    }
    else if (exitValue == 0) {
      consoleOutput.message(MavenBuildLogger.LEVEL_INFO, BuilderBundle.message("external.process.finished", exitValue), null);
    }
    else {
      consoleOutput.message(MavenBuildLogger.LEVEL_ERROR, BuilderBundle.message("external.process.terminated.abnormally", exitValue), null);
    }
  }

  private void logCommand(List<String> executionCommand) {
    StringBuffer command = new StringBuffer();
    for (String anExecutionCommand : executionCommand) {
      command.append(anExecutionCommand).append(" ");
    }
    command.append(LogBroadcaster.LINE_SEPARATOR);
    consoleOutput.print(command.toString());
  }

  int stop() {
    super.stop();
    return mavenProcess == null ? 0 : destroyProcess(mavenProcess);
  }

  public String getCaption() {
    return BuilderBundle.message("external.executor.caption");
  }

  private void readProcessOutput() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    BufferedReader procout = new BufferedReader(new InputStreamReader(mavenProcess.getInputStream()));
    try {
      String line;
      while ((line = procout.readLine()) != null) {
        if (indicator != null) {
          if (indicator.isCanceled()) {
            cancel();
          }
          if (line.matches(INFO_REGEXP)) {
            indicator.setText2(line.substring(INFO_PREFIX_SIZE));
          }
        }
        consoleOutput.print(line);
      }
    }
    catch (IOException e) {
      consoleOutput.message(MavenBuildLogger.LEVEL_ERROR, BuilderBundle.message("external.io.error"), e);
      try {
        procout.close();
      }
      catch (IOException ignore) {
      }
    }
  }

  private static int destroyProcess(@NotNull Process process) {
    try {
      return process.exitValue();
    }
    catch (IllegalThreadStateException e) {
      process.destroy();

      try {
        process.waitFor();
      }
      catch (InterruptedException e1) {
        LOG.error(e1);
      }
      return process.exitValue();
    }
  }

  public MavenBuildLogger getLogger() {
    return buildLogger;
  }
}
