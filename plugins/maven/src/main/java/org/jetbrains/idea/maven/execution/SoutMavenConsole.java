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

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.Key;
import org.jetbrains.idea.maven.project.MavenConsole;

public class SoutMavenConsole extends MavenConsole {
  public SoutMavenConsole() {
    super(MavenExecutionOptions.LoggingLevel.DEBUG, true);
  }

  public boolean canPause() {
    return false;
  }

  public boolean isOutputPaused() {
    return false;
  }

  public void setOutputPaused(boolean outputPaused) {
  }

  public void attachToProcess(ProcessHandler processHandler) {
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        System.out.print(event.getText());
      }

      @Override
      public void processTerminated(ProcessEvent event) {
        System.out.println("PROCESS TERMINATED: " + event.getExitCode());
      }
    });
  }

  protected void doPrint(String text, OutputType type) {
    System.out.print(text);
  }
}
