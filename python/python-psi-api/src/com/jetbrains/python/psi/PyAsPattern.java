package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

public interface PyAsPattern extends PyPattern {
  @NotNull PyPattern getPattern();
}
