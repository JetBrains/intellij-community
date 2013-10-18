package com.jetbrains.python.console;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public interface PyCodeExecutor {
  void executeCode(@NotNull String code, @Nullable Editor e);
}
