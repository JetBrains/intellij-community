package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PySubscriptableType extends PyType {
  @Nullable
  PyType getElementType(PyExpression index, TypeEvalContext context);
}
