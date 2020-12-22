// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.xsltDebugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.intellij.lang.xpath.xslt.run.XsltCommandLineState;
import org.intellij.lang.xpath.xslt.run.XsltRunConfiguration;
import org.intellij.plugins.xsltDebugger.impl.XsltDebugProcess;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class XsltDebuggerRunner implements ProgramRunner<RunnerSettings> {
  static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

  @Override
  public @NotNull String getRunnerId() {
    return "XsltDebuggerRunner";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return executorId.equals("Debug") && profile instanceof XsltRunConfiguration;
  }

  @Override
  public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
    ExecutionManager.getInstance(environment.getProject()).startRunProfile(environment, Objects.requireNonNull(environment.getState()), state -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      return createContentDescriptor(state, environment);
    });
  }

  private RunContentDescriptor createContentDescriptor(RunProfileState runProfileState, @NotNull ExecutionEnvironment environment)
    throws ExecutionException {
    XDebugSession debugSession =
      XDebuggerManager.getInstance(environment.getProject()).startSession(environment, new XDebugProcessStarter() {
        @Override
        public @NotNull XDebugProcess start(final @NotNull XDebugSession session) throws ExecutionException {
          ACTIVE.set(Boolean.TRUE);
          try {
            final XsltCommandLineState c = (XsltCommandLineState)runProfileState;
            final ExecutionResult result = runProfileState.execute(environment.getExecutor(), XsltDebuggerRunner.this);
            return new XsltDebugProcess(session, result, c.getExtensionData().getUserData(XsltDebuggerExtension.VERSION));
          }
          finally {
            ACTIVE.remove();
          }
        }
      });
    return debugSession.getRunContentDescriptor();
  }
}