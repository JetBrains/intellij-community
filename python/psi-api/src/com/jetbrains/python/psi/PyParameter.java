// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Abstract function parameter; may cover either a named parameter or a tuple of parameters.
 * @see com.jetbrains.python.psi.impl.ParamHelper
 * User: dcheryasov
 */
public interface PyParameter extends PyElement {

  /**
   * @return the named parameter which is represented by this parameter, or null if the parameter is a tuple.
   */
  @Nullable
  PyNamedParameter getAsNamed();

  /**
   * @return the tuple parameter which is represented by this parameter, or null if the parameter is named.
   */
  @Nullable
  PyTupleParameter getAsTuple();

  @Nullable
  PyExpression getDefaultValue();

  boolean hasDefaultValue();

  /**
   * @apiNote This method will be marked as abstract in 2018.2.
   */
  @Nullable
  default String getDefaultValueText() {
    return null;
  }

  /**
   * @return true if the parameter is the 'self' parameter of an instance attribute function or a function
   * annotated with @classmethod
   */
  boolean isSelf();
}
