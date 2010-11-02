package com.jetbrains.python.console;

import com.jetbrains.python.console.pydev.ICallback;
import com.jetbrains.python.console.pydev.InterpreterResponse;

/**
 * @author traff
 */
public interface ConsoleCommandExecutor {
  boolean isWaitingForInput();

  void execInterpreter(String s, ICallback<Object, InterpreterResponse> callback);
}
