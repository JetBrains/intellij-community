// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstParameter;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract function parameter; may cover either a named parameter or a tuple of parameters.
 * @see com.jetbrains.python.psi.impl.ParamHelper
 */
public interface PyParameter extends PyAstParameter, PyElement {

  /**
   * @return the named parameter which is represented by this parameter, or null if the parameter is a tuple.
   */
  @Override
  @Nullable
  PyNamedParameter getAsNamed();

  /**
   * @return the tuple parameter which is represented by this parameter, or null if the parameter is named.
   */
  @Override
  @Nullable
  PyTupleParameter getAsTuple();

  @Override
  @Nullable
  PyExpression getDefaultValue();

  /**
   * @return true if the parameter is the 'self' parameter of an instance attribute function or a function
   * annotated with @classmethod
   */
  boolean isSelf();
}
