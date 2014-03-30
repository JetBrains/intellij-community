package com.jetbrains.python.console.pydev;

/**
 * @author traff
 */
public interface ConsoleCommunicationListener {
  void commandExecuted(boolean more);
  void inputRequested();
}
