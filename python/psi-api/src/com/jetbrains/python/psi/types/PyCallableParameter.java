package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public interface PyCallableParameter {
  @Nullable
  String getName();

  @Nullable
  PyType getType(@NotNull TypeEvalContext context);

  @Nullable
  PyParameter getParameter();
}
