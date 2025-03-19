package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

public class PyReparseableEndOfLineCommentType extends PyReparseableTokenTypeWithSimpleCheck {

  public PyReparseableEndOfLineCommentType(@NotNull String debugName) {
    super(debugName);
  }

  @Override
  public boolean isReparseable(@NotNull String newText) {
    return newText.startsWith("#");
  }
}
