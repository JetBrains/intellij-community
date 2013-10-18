package com.jetbrains.python.psi;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyLambdaExpression extends PyExpression, Callable, ScopeOwner {
  @Nullable
  PyExpression getBody();
}
