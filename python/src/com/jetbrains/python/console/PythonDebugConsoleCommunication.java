package com.jetbrains.python.console;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.console.pydev.*;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.apache.commons.lang.NotImplementedException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class PythonDebugConsoleCommunication extends AbstractConsoleCommunication {
  private final PyDebugProcess myDebugProcess;
  private final ConsoleViewImpl myTextConsoleView;

  private final StringBuilder myExpression = new StringBuilder();


  public PythonDebugConsoleCommunication(Project project, PyDebugProcess debugProcess, ConsoleViewImpl textConsoleView) {
    super(project);
    myDebugProcess = debugProcess;
    myTextConsoleView = textConsoleView;
  }

  @NotNull
  @Override
  public List<PydevCompletionVariant> getCompletions(String prefix) throws Exception {
    return myDebugProcess.getCompletions(prefix);
  }

  @Override
  public String getDescription(String text) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean isWaitingForInput() {
    return false;
  }

  @Override
  public boolean isExecuting() {
    return false;
  }

  protected Pair<String, Boolean> exec(final String command) throws PyDebuggerException {
    String value = myDebugProcess.consoleExec(command);
    return parseExecResponseString(value);
  }

  public void execInterpreter(
    String s,
    ICallback<Object, InterpreterResponse> callback) {
    try {
      myExpression.append(s);
      Pair<String, Boolean> executed = exec(myExpression.toString());

      boolean more = executed.second;

      if (!more) {
        myExpression.setLength(0);
      }
      callback.call(new InterpreterResponse(more, isWaitingForInput()));
    }
    catch (PyDebuggerException e) {
      myExpression.setLength(0);
      callback.call(new InterpreterResponse(false, isWaitingForInput()));
    }
  }

  @Override
  public void interrupt() {
    throw new NotImplementedException();
  }
}
