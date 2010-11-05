package com.jetbrains.python.console.pydev;

import java.util.List;

/**
 * @author traff
 */
public interface ConsoleCommunication {
  List<PydevCompletionVariant> getCompletions(String prefix) throws Exception;

  String getDescription(String text) throws Exception;

  boolean isWaitingForInput();

  void execInterpreter(String s, ICallback<Object,InterpreterResponse> callback);
}
