/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.console;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.jetbrains.python.console.pydev.AbstractConsoleCommunication;
import com.jetbrains.python.console.pydev.InterpreterResponse;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class PythonDebugConsoleCommunication extends AbstractConsoleCommunication {
  private final PyDebugProcess myDebugProcess;

  private final StringBuilder myExpression = new StringBuilder();


  public PythonDebugConsoleCommunication(Project project, PyDebugProcess debugProcess) {
    super(project);
    myDebugProcess = debugProcess;
  }

  @NotNull
  @Override
  public List<PydevCompletionVariant> getCompletions(String text, String actualToken) throws Exception {
    return myDebugProcess.getCompletions(actualToken);
  }

  @Override
  public String getDescription(String text) {
    return null;
  }

  @Override
  public boolean isWaitingForInput() {
    return false;
  }

  @Override
  public boolean isExecuting() {
    return false;
  }

  protected void exec(final ConsoleCodeFragment command, final PyDebugCallback<Pair<String, Boolean>> callback) {
    myDebugProcess.consoleExec(command.getText(), new PyDebugCallback<String>() {
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

  public void execInterpreter(ConsoleCodeFragment code, final Function<InterpreterResponse, Object> callback) {
    myExpression.append(code.getText());
    exec(new ConsoleCodeFragment(myExpression.toString(), false), new PyDebugCallback<Pair<String, Boolean>>() {
      @Override
      public void ok(Pair<String, Boolean> executed) {
        boolean more = executed.second;

        if (!more) {
          myExpression.setLength(0);
        }
        callback.fun(new InterpreterResponse(more, isWaitingForInput()));
      }

      @Override
      public void error(PyDebuggerException exception) {
        myExpression.setLength(0);
        callback.fun(new InterpreterResponse(false, isWaitingForInput()));
      }
    });
  }

  @Override
  public void interrupt() {
    throw new UnsupportedOperationException();
  }

  public boolean isSuspended() {
    return myDebugProcess.getSession().isSuspended();
  }
}
