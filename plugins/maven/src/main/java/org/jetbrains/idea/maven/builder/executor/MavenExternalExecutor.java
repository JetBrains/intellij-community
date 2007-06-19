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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.DefaultJavaProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.builder.BuilderBundle;
import org.jetbrains.idea.maven.builder.MavenBuilderState;
import org.jetbrains.idea.maven.builder.logger.MavenLogUtil;
import org.jetbrains.idea.maven.core.MavenCoreState;

public class MavenExternalExecutor extends MavenExecutor {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.builder.executor.MavenExternalExecutor");

  private OSProcessHandler myProcessHandler;

  @NonNls private static final String PHASE_INFO_REGEXP = "\\[INFO\\] \\[.*:.*\\]";
  @NonNls private static final int INFO_PREFIX_SIZE = "[INFO] ".length();

  public MavenExternalExecutor(MavenBuildParameters parameters, MavenCoreState mavenCoreState, MavenBuilderState builderState) {
    super(parameters, mavenCoreState, builderState, BuilderBundle.message("external.executor.caption"));
  }

  public void run() {
    displayProgress();

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    try {
      myProcessHandler =
        new DefaultJavaProcessHandler(MavenExternalParameters.createJavaParameters(myParameters, myCoreState, myBuilderState)) {
          public void notifyTextAvailable(final String text, final Key outputType) {
            if (isNotSuppressed(MavenLogUtil.getLevel(text))) {
              super.notifyTextAvailable(text, outputType);
            }
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
        };

      attachToProcess(myProcessHandler);
    }
    catch (ExecutionException e) {
      LOG.warn(e.getMessage(), e);
      systemMessage(MavenLogUtil.LEVEL_FATAL, BuilderBundle.message("external.statup.failed"), e);
      return;
    }

    start();
    readProcessOutput();
    int exitValue = stop();

    if (isCancelled()) {
      systemMessage(MavenLogUtil.LEVEL_INFO, BuilderBundle.message("external.process.aborted", exitValue), null);
    }
    else if (exitValue == 0) {
      systemMessage(MavenLogUtil.LEVEL_INFO, BuilderBundle.message("external.process.finished", exitValue), null);
    }
    else {
      systemMessage(MavenLogUtil.LEVEL_ERROR, BuilderBundle.message("external.process.terminated.abnormally", exitValue), null);
    }
  }

  int stop() {
    super.stop();
    if (myProcessHandler == null) {
      return 0;
    }
    myProcessHandler.destroyProcess();
    myProcessHandler.waitFor();
    return myProcessHandler.getProcess().exitValue();
  }

  private void readProcessOutput() {
    myProcessHandler.startNotify();
    myProcessHandler.waitFor();
  }
}
