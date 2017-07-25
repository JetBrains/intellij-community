/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration;
import com.jetbrains.cidr.execution.debugger.remote.CidrRemoteDebugParameters;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.cidr.execution.debugger.remote.CidrRemoteGDBDebugProcessKt.createParams;

public class MixedCidrDebugProcess extends CidrDebugProcess {

  public MixedCidrDebugProcess(DebuggerDriverConfiguration driverConfiguration,
                               GeneralCommandLine generalCommandLine,
                               CidrRemoteDebugParameters parameters,
                               XDebugSession session,
                               TextConsoleBuilder consoleBuilder,
                               XDebuggerEditorsProvider editorsProvider) throws ExecutionException {
    super(createParams(driverConfiguration, generalCommandLine), session, consoleBuilder, editorsProvider);
  }

  @Override
  public boolean isDetachDefault() {
    return true;
  }

  @Override
  public void doStart(@NotNull DebuggerDriver driver) throws ExecutionException {
    driver.loadForLaunch();
  }

  @Override
  public void doLaunchTarget(@NotNull DebuggerDriver driver) throws ExecutionException {
    driver.setRedirectOutputToFiles(true);
    driver.launch();
  }
}