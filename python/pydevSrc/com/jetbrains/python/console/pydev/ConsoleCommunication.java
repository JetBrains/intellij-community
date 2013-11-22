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

  void execInterpreter(ConsoleCodeFragment code, Function<InterpreterResponse, Object> callback);

  void interrupt();

  void addCommunicationListener(ConsoleCommunicationListener listener);

  void notifyCommandExecuted(boolean more);
  void notifyInputRequested();

  class ConsoleCodeFragment {
    private final String myText;
    private final boolean myIsSingleLine;

    public ConsoleCodeFragment(String text, boolean isSingleLine) {
      myText = text;
      myIsSingleLine = isSingleLine;
    }

    public String getText() {
      return myText;
    }

    public boolean isSingleLine() {
      return myIsSingleLine;
    }
  }
}
