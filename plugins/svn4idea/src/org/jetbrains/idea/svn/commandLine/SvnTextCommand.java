/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 3:09 PM
 */
public abstract class SvnTextCommand extends SvnCommand {
  private boolean myIsDestroyed;
  private OSProcessHandler myHandler;

  public SvnTextCommand(Project project, File workingDirectory, @NotNull SvnCommandName commandName) {
    super(project, workingDirectory, commandName);
  }

  @Override
  protected void waitForProcess() {
    if (myHandler != null) {
      myHandler.waitFor();
    }
  }

  @Override
  protected Process startProcess() throws ExecutionException {
    if (myIsDestroyed) return null;
    final Process process = myCommandLine.createProcess();
    myHandler = new OSProcessHandler(process, myCommandLine.getCommandLineString());
    return myHandler.getProcess();
  }

  @Override
  protected void startHandlingStreams() {
    if (myIsDestroyed || myProcess == null) return;

    myHandler.addProcessListener(new ProcessListener() {
      public void startNotified(final ProcessEvent event) {
        // do nothing
      }

      public void processTerminated(final ProcessEvent event) {
        final int exitCode = event.getExitCode();
        try {
          setExitCode(exitCode);
          //cleanupEnv();   todo
          SvnTextCommand.this.processTerminated(exitCode);
        } finally {
          listeners().processTerminated(exitCode);
        }
      }

      public void processWillTerminate(final ProcessEvent event, final boolean willBeDestroyed) {
        // do nothing
      }

      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        SvnTextCommand.this.onTextAvailable(event.getText(), outputType);
      }
    });
    myHandler.startNotify();
  }

  protected abstract void processTerminated(int exitCode);
  protected abstract void onTextAvailable(final String text, final Key outputType);

  @Override
  public void destroyProcess() {
    myIsDestroyed = true;
    if (myHandler != null) {
      myHandler.destroyProcess();
    }
  }
}
