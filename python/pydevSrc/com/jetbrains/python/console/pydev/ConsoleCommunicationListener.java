package com.jetbrains.python.console.pydev;

/**
 * @author traff
 */
public interface ConsoleCommunicationListener {
  void commandExecuted();
  void inputRequested();
}
