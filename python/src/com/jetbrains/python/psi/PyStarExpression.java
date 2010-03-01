package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 27.02.2010
 * Time: 14:20:22
 */
public interface PyStarExpression extends PyExpression {
  @Nullable
  PyExpression getExpression();
}
