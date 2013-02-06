package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Abstract function parameter; may cover either a named parameter or a tuple of parameters.
 * @see com.jetbrains.python.psi.impl.ParamHelper
 * User: dcheryasov
 * Date: Jul 5, 2009 8:30:13 PM
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
   * @return true if the parameter is the 'self' parameter of an instance attribute function or a function
   * annotated with @classmethod
   */
  boolean isSelf();
}
