/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Result of analysis of argument list application to the callee.
 * Contains neatly arranged lists and mappings between arguments and parameters,
 * including error diagnostics.
 */
public interface CallArgumentsMapping {
  /**
   * @return A mapping argument->parameter for non-starred parameters (but includes starred argument).
   */
  @NotNull
  Map<PyExpression, PyNamedParameter> getPlainMappedParams();

  /**
   * Consider a piece of Python 2.x code:
   * <pre>
   * def f(a, (b, c,), d):
   *   ...
   *
   * x = (1, 2)
   * f(10, x, 20)
   * </pre>
   * Here, argument <tt>x</tt> successfully maps to both <tt>b</tt> and <tt>c</tt> parameters.
   * This case is rare, so a separate method is introduced, to keep {@link CallArgumentsMapping#getPlainMappedParams()} simple.
   * @return mapping of arguments to nested parameters that get collectively mapped to that argument.
   */
  @NotNull Map<PyExpression, List<PyNamedParameter>> getNestedMappedParams();

  /**
   * @return First *arg, or null.
   */
  @Nullable
  PyStarArgument getTupleArg();

  /**
   * @return A list of parameters mapped to a *arg.
   */
  @NotNull List<PyNamedParameter> getTupleMappedParams();

  /**
   * @return First **arg, or null.
   */
  @Nullable
  PyStarArgument getKwdArg();

  /**
   * @return A list of parameters mapped to an **arg.
   */
  @NotNull List<PyNamedParameter> getKwdMappedParams();

  /**
   * @return A list of parameters for which no arguments were found ('missing').
   */
  @NotNull
  List<PyNamedParameter> getUnmappedParams();


  /**
   * @return Lists all args with their flags.
   * @see com.jetbrains.python.psi.CallArgumentsMapping.ArgFlag
   */
  Map<PyExpression, EnumSet<ArgFlag>> getArgumentFlags();

  boolean hasProblems();
  
  /**
   * @return result of a resolveCallee() against the function call to which the parameter list belongs.
   */
  @Nullable
  PyCallExpression.PyMarkedCallee getMarkedCallee();

  PyArgumentList getArgumentList();

  /**
   * Flags to mark analysis results for an argument.
   * Theoretically can be used together, but currently only make sense as a single value per argument.
   */
  enum ArgFlag {
    /** duplicate plain */          IS_DUP,
    /** unexpected */               IS_UNMAPPED,
    /** duplicate **arg */          IS_DUP_KWD,
    /** duplicate *arg */           IS_DUP_TUPLE,
    /** positional past keyword */  IS_POS_PAST_KWD,
    /** *param is too long */       IS_TOO_LONG,
  }
}
