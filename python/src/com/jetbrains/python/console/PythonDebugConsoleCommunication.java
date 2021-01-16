// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.Function;
import com.jetbrains.python.console.pydev.AbstractConsoleCommunication;
import com.jetbrains.python.console.pydev.InterpreterResponse;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;

public class PythonDebugConsoleCommunication extends AbstractConsoleCommunication {
  private static final Logger LOG = Logger.getInstance(PythonDebugConsoleCommunication.class);
  private final PyDebugProcess myDebugProcess;
  private boolean myNeedsMore = false;
  private boolean firstExecution = true;
  @NotNull private final PythonConsoleView myConsoleView;

  public PythonDebugConsoleCommunication(@NotNull Project project,
                                         @NotNull PyDebugProcess debugProcess,
                                         @NotNull PythonConsoleView consoleView) {
    super(project);
    myDebugProcess = debugProcess;
    myConsoleView = consoleView;
  }

  @NotNull
  @Override
  public List<PydevCompletionVariant> getCompletions(String text, String actualToken) throws Exception {
    return myDebugProcess.getCompletions(actualToken);
  }

  @Override
  public String getDescription(String refExpression) throws Exception {
    return myDebugProcess.getDescription(refExpression);
  }

  @Override
  public boolean isWaitingForInput() {
    return waitingForInput;
  }

  @Override
  public boolean needsMore() {
    return myNeedsMore;
  }

  @Override
  public boolean isExecuting() {
    return false;
  }

  protected void exec(ConsoleCodeFragment command, final PyDebugCallback<Pair<String, Boolean>> callback) {
    if (firstExecution) {
      firstExecution = false;
      myConsoleView.addConsoleFolding(true, false);
    }
    myDebugProcess.consoleExec(command.getText(), new PyDebugCallback<>() {
      @Override
      public void ok(String value) {
        callback.ok(parseExecResponseString(value));
      }

      @Override
      public void error(PyDebuggerException exception) {
        callback.error(exception);
      }
    });
  }

  @Override
  public void execInterpreter(ConsoleCodeFragment code, final Function<InterpreterResponse, Object> callback) {
    if (waitingForInput) {
      final OutputStream processInput = myDebugProcess.getProcessHandler().getProcessInput();
      if (processInput != null) {
        try {
          final Charset defaultCharset = EncodingProjectManager.getInstance(myDebugProcess.getProject()).getDefaultCharset();
          processInput.write((code.getText()).getBytes(defaultCharset));
          processInput.flush();

        }
        catch (IOException e) {
          LOG.error(e.getMessage());
        }
      }
      myNeedsMore = false;
      waitingForInput = false;
      notifyCommandExecuted(waitingForInput);

    }
    else {

      exec(new ConsoleCodeFragment(code.getText(), false), new PyDebugCallback<>() {
        @Override
        public void ok(Pair<String, Boolean> executed) {
          boolean more = executed.second;
          myNeedsMore = more;
          notifyCommandExecuted(more);
          callback.fun(new InterpreterResponse(more, isWaitingForInput()));
        }

        @Override
        public void error(PyDebuggerException exception) {
          myNeedsMore = false;
          notifyCommandExecuted(false);
          callback.fun(new InterpreterResponse(false, isWaitingForInput()));
        }
      });
    }
  }

  @Override
  public void notifyInputRequested() {
    waitingForInput = true;
    super.notifyInputRequested();
  }


  @Override
  public void interrupt() {
    throw new UnsupportedOperationException();
  }

  public boolean isSuspended() {
    return myDebugProcess.getSession().isSuspended();
  }

  public void resume() {
    myDebugProcess.getSession().resume();
  }
}
