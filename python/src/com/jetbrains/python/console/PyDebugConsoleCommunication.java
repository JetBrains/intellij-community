package com.jetbrains.python.console;

import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebugProcess;

import java.util.List;

/**
 * @author traff
 */
public class PyDebugConsoleCommunication implements ConsoleCommunication {
  private final PyDebugProcess myDebugProcess;

  public PyDebugConsoleCommunication(PyDebugProcess debugProcess) {
    myDebugProcess = debugProcess;
  }

  @Override
  public List<PydevCompletionVariant> getCompletions(String prefix) throws Exception {
    return myDebugProcess.getCompletions(prefix);
  }

  @Override
  public String getDescription(String text) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
