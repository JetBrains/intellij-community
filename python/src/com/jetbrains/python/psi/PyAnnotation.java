package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyAnnotation extends PyElement {
  PyExpression getValue();

  @Nullable
  PyClass resolveToClass();
}
