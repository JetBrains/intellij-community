// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console.pydev;

import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ConsoleCommunication {
  @NotNull
  List<PydevCompletionVariant> getCompletions(String text, String actualToken) throws Exception;

  String getDescription(String text) throws Exception;

  boolean isWaitingForInput();

  boolean isExecuting();

  boolean needsMore();

  void execInterpreter(ConsoleCodeFragment code, Function<InterpreterResponse, Object> callback);

  void interrupt();

  void addCommunicationListener(ConsoleCommunicationListener listener);

  void notifyCommandExecuted(boolean more);
  void notifyInputRequested();

  void notifyInputReceived();

  class ConsoleCodeFragment {
    private @NonNls String myText;
    private final boolean myIsSingleLine;

    public ConsoleCodeFragment(@NonNls String text, boolean isSingleLine) {
      myText = text;
      myIsSingleLine = isSingleLine;
    }

    public @NonNls String getText() {
      return myText;
    }

    public void setText(@NonNls String text) {
      myText = text;
    }

    public boolean isSingleLine() {
      return myIsSingleLine;
    }
  }
}
