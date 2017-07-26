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
import com.intellij.xdebugger.frame.XExecutionStack;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.CidrStackFrame;
import com.jetbrains.cidr.execution.debugger.CidrSuspensionCause;
import com.jetbrains.cidr.execution.debugger.backend.*;
import com.jetbrains.cidr.execution.debugger.memory.Address;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.cidr.execution.debugger.remote.CidrRemoteGDBDebugProcessKt.createParams;

public class MixedCidrDebugProcess extends CidrDebugProcess {

  public MixedCidrDebugProcess(DebuggerDriverConfiguration driverConfiguration,
                               GeneralCommandLine generalCommandLine,
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

  @NotNull
  protected XExecutionStack newExecutionStack(@NotNull LLThread thread,
                                              @Nullable LLFrame frame,
                                              boolean current,
                                              @Nullable CidrSuspensionCause cause) {
    return new MixedCidrExecutionStack(thread, frame, current, cause);
  }


  protected class MixedCidrExecutionStack extends CidrExecutionStack {
    public MixedCidrExecutionStack(@NotNull LLThread thread,
                                   @Nullable LLFrame frame,
                                   boolean current,
                                   @Nullable CidrSuspensionCause suspensionCause) {
      super(thread, frame, current, suspensionCause);
    }

    @NotNull
    private CidrStackFrame newFrame(@NotNull LLFrame frame) {
      return new CidrStackFrame(MixedCidrDebugProcess.this, myThread, frame, mySuspensionCause);
    }


    @Override
    protected void handleNewFrame(@NotNull DebuggerDriver driver,
                                  @NotNull List<CidrStackFrame> result,
                                  @Nullable CidrStackFrame frame) throws ExecutionException, DebuggerCommandException {
      if (frame != null && frame.getFrame().getFunction().equals("PyEval_EvalFrameEx")) {
        List<LLValue> variables = driver.getVariables(frame.getThreadId(), frame.getFrameIndex());

        if (variables.stream().anyMatch(x -> x.getType().equals("PyFrameObject *"))) {
          int currentLine = Integer.parseInt(driver.getData(driver.evaluate(frame.getThreadId(), frame.getFrameIndex(),
                                                                            " PyFrame_GetLineNumber(f)")).getValue());

          Address frameAddress = Address.parseHexString(driver.getData(driver.evaluate(frame.getThreadId(), frame.getFrameIndex(),
                                                                                       "((PyObject)f)._ob_next")).getValue());

          String function = driver.getData(driver.evaluate(frame.getThreadId(), frame.getFrameIndex(),
                                                           "PyString_AsString(f->f_code->co_name)")).getDescription();

          String filename = driver.getData(driver.evaluate(frame.getThreadId(), frame.getFrameIndex(),
                                                           "PyString_AsString(f->f_code->co_filename)")).getDescription();
          assert filename != null && function != null;

          if (!filename.isEmpty()) {
            filename = filename.substring(1, filename.length() - 1);
            function = function.substring(1, function.length() - 1);
            LLFrame llFrame = new LLFrame(frame.getFrameIndex(), function, filename, currentLine - 1, frameAddress, null, false);
            result.add(newFrame(llFrame));
          }
        }
      }
      result.add(frame);
    }
  }
}
