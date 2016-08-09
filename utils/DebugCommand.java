package org.jetbrains.debugger.memory.utils;

import com.intellij.debugger.engine.managerThread.DebuggerCommand;

public interface DebugCommand extends DebuggerCommand {
  @Override
  default void commandCancelled() {
  }
}
