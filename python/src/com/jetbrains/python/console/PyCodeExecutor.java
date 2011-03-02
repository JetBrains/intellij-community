package com.jetbrains.python.console;

import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public interface PyCodeExecutor {
  void executeCode(@NotNull String code);
}
