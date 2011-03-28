package com.jetbrains.python.console.pydev;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public interface ConsoleCommunication {
  @NotNull
  List<PydevCompletionVariant> getCompletions(String prefix) throws Exception;

  String getDescription(String text) throws Exception;

  boolean isWaitingForInput();

  void execInterpreter(String s, ICallback<Object,InterpreterResponse> callback);
}
