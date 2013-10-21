package com.jetbrains.python.console.pydev;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public interface ConsoleCommunication {
  @NotNull
  List<PydevCompletionVariant> getCompletions(String text, String actualToken) throws Exception;

  String getDescription(String text) throws Exception;

  boolean isWaitingForInput();

  boolean isExecuting();

  void execInterpreter(String s, Function<InterpreterResponse, Object> callback);

  void interrupt();

  void addCommunicationListener(ConsoleCommunicationListener listener);

  void notifyCommandExecuted();
  void notifyInputRequested();

}
