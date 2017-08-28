/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  boolean hasDefaultNoneValue();

  /**
   * @return true if the parameter is the 'self' parameter of an instance attribute function or a function
   * annotated with @classmethod
   */
  boolean isSelf();
}
