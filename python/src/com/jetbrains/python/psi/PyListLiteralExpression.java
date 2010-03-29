package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PyListLiteralExpression extends PyExpression {
  @NotNull
  PyExpression[] getElements();
}
