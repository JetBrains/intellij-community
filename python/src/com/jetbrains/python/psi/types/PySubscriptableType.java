package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PySubscriptableType extends PyType {

  /**
   * Access elements by a constant Python expression
   * @param index an expression that should evaluate to integer, zero-based
   * @param context for type evaluation (think caching)
   * @return type of item
   */
  @Nullable
  PyType getElementType(PyExpression index, TypeEvalContext context);

  /**
   * Access elements by zero-based index.
   * @param index
   * @return type of item
   */
  @Nullable
  PyType getElementType(int index);

  int getElementCount();
}
