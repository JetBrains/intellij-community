package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstAsPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PyAsPattern extends PyAstAsPattern, PyPattern {
  @Override
  default @NotNull PyPattern getPattern() {
    return (PyPattern)PyAstAsPattern.super.getPattern();
  }

  @Override
  default @Nullable PyTargetExpression getTarget() {
    return (PyTargetExpression)PyAstAsPattern.super.getTarget();
  }
}
