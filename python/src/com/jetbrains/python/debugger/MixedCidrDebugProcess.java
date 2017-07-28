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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectRootUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.CidrStackFrame;
import com.jetbrains.cidr.execution.debugger.CidrSuspensionCause;
import com.jetbrains.cidr.execution.debugger.backend.*;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static com.jetbrains.cidr.execution.debugger.remote.CidrRemoteGDBDebugProcessKt.createParams;

public class MixedCidrDebugProcess extends CidrDebugProcess {

  private static final Logger LOG = Logger.getInstance("#" + MixedCidrDebugProcess.class.getPackage().getName());


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


    @Override
    protected void handleNewFrame(@NotNull DebuggerDriver driver,
                                  @NotNull List<XStackFrame> result,
                                  @NotNull CidrStackFrame frame) throws ExecutionException, DebuggerCommandException {
      result.add(frame);
      if (frame.getFrame().getFunction().equals("PyEval_EvalFrameEx")) {
        List<LLValue> variables = driver.getVariables(frame.getThreadId(), frame.getFrameIndex());
        Optional<LLValue> pythonInnerFrame = variables.stream().filter(x -> x.getType().equals("PyFrameObject *")).findAny();

        if (pythonInnerFrame.isPresent()) {
          try {
            String frameVariableName = pythonInnerFrame.get().getName();

            int innerFrameLineNumber = Integer.parseInt(
              evaluateAndGetLLValueData(driver, frame, "PyFrame_GetLineNumber(%s)", frameVariableName).getValue()) - 1;

            final String innerFrameFilename = evaluateAndGetLLValueData(driver, frame, "PyString_AsString(%s->f_code->co_filename)", frameVariableName).getDescription();

            if (innerFrameFilename != null && !innerFrameFilename.isEmpty()) {
              VirtualFile innerFrameVirtualFile = ReadAction.compute(() -> {
                VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(innerFrameFilename.substring(1, innerFrameFilename.length() - 1));
                if (vFile == null) return null;
                return ProjectRootUtil.findSymlinkedFileInContent(getProject(), vFile);
              });

              result.add(new MixedPythonStackFrame(innerFrameVirtualFile, innerFrameLineNumber, frame.getFrameIndex(), frame.getThreadId()));
            } else {
              LOG.warn("Filename is null or empty: " + frame);
            }
          } catch (NumberFormatException e) {
            LOG.warn(frame.toString(), e);
          }
        }
      }
    }

    private class MixedPythonStackFrame extends XStackFrame {
      public final VirtualFile myFile;
      public final int myLine;
      public final int myId;
      public final long myThreadId;

      public MixedPythonStackFrame(VirtualFile file, int line, int id, long threadId) {
        this.myFile = file;
        this.myLine = line;
        this.myId = id;
        this.myThreadId = threadId;
      }

      @Nullable
      @Override
      public Object getEqualityObject() {
        return Pair.create(-myThreadId, myId);
      }

      @Nullable
      @Override
      public XSourcePosition getSourcePosition() {
        return XDebuggerUtil.getInstance().createPosition(myFile, myLine);
      }
    }

    private LLValueData evaluateAndGetLLValueData(@NotNull DebuggerDriver driver,
                                                  @NotNull CidrStackFrame frame,
                                                  @NotNull @PrintFormat String command,
                                                  @NotNull Object... args)
      throws ExecutionException, DebuggerCommandException {
      return driver.getData(driver.evaluate(frame.getThreadId(), frame.getFrameIndex(),
                                                                        String.format(command, args)));
    }
  }
}
