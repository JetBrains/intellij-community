package com.jetbrains.python.debugger;

import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyPositionConverter {

  @NotNull
  PySourcePosition create(@NotNull final String file, final int line);

  @NotNull
  PySourcePosition convert(@NotNull final XSourcePosition position);

  @Nullable
  XSourcePosition convert(@NotNull final PySourcePosition position);

}
