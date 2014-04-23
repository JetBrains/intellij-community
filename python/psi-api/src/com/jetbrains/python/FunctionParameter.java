package com.jetbrains.python;

import org.jetbrains.annotations.Nullable;

/**
 * This class (possibly enum) represents function parameter
 *
 * @author Ilya.Kazakevich
 */
public interface FunctionParameter {
  /**
   * @return parameter position
   */
  int getPosition();

  /**
   * @return parameter name (if known)
   */
  @Nullable
  String getName();
}
