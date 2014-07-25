package com.jetbrains.python;

import org.jetbrains.annotations.Nullable;

/**
 * This class (possibly enum) represents function parameter
 *
 * @author Ilya.Kazakevich
 */
public interface FunctionParameter {
  /**
   * Position value if argument is keyword-only
   */
  int POSITION_NOT_SUPPORTED = -1;

  /**
   * @return parameter position. Be sure to check position is supported (!= {@link #POSITION_NOT_SUPPORTED} )
   * @see #POSITION_NOT_SUPPORTED
   */
  int getPosition();

  /**
   * @return parameter name (if known)
   */
  @Nullable
  String getName();
}
