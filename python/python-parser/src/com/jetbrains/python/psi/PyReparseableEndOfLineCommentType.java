package com.jetbrains.python.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class PyReparseableEndOfLineCommentType extends PyReparseableTokenTypeWithSimpleCheck {

  public PyReparseableEndOfLineCommentType(@NotNull String debugName) {
    super(debugName);
  }

  @Override
  public boolean isReparseable(@NotNull String newText) {
    return newText.startsWith("#");
  }
}
