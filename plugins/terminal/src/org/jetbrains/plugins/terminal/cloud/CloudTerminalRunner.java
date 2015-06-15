/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.terminal.cloud;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.AbstractTerminalRunner;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;

public class CloudTerminalRunner extends AbstractTerminalRunner<CloudTerminalProcess> {
  private final String myPipeName;
  private CloudTerminalProcess myProcess;

  public CloudTerminalRunner(@NotNull Project project, String pipeName, CloudTerminalProcess process) {
    super(project);
    myPipeName = pipeName;
    myProcess = process;
  }

  @Override
  protected CloudTerminalProcess createProcess(@Nullable String directory) throws ExecutionException {
    return myProcess;
  }

  @Override
  protected ProcessHandler createProcessHandler(final CloudTerminalProcess process) {
    return new ProcessHandler() {

      @Override
      protected void destroyProcessImpl() {
        process.destroy();
      }

      @Override
      protected void detachProcessImpl() {
        process.destroy();
      }

      @Override
      public boolean detachIsDefault() {
        return false;
      }

      @Nullable
      @Override
      public OutputStream getProcessInput() {
        return process.getOutputStream();
      }
    };
  }

  @Override
  protected String getTerminalConnectionName(CloudTerminalProcess process) {
    return "Terminal: " + myPipeName;
  }

  @Override
  protected TtyConnector createTtyConnector(CloudTerminalProcess process) {
    return new ProcessTtyConnector(process, Charset.defaultCharset()) {

      @Override
      protected void resizeImmediately() {

      }

      @Override
      public String getName() {
        return "Connector: " + myPipeName;
      }

      @Override
      public boolean isConnected() {
        return true;
      }
    };
  }

  @Override
  public String runningTargetName() {
    return "Cloud terminal";
  }
}
