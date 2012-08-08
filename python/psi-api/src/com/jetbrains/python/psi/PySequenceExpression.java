package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PySequenceExpression extends PyExpression{
  @NotNull
  PyExpression[] getElements();
}
