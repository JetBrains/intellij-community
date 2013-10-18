package com.jetbrains.python.psi;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Optionally typed Python element.
 *
 * @author vlan
 */
public interface PyTypedElement extends PyElement {
  @Nullable
  PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key);
}
