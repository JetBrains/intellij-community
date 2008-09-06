/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents an argument list of a function call.
 * User: yole
 * Date: 29.05.2005
 */
public interface PyArgumentList extends PyElement {

  @NotNull PyExpression[] getArguments();

  @Nullable PyKeywordArgument getKeywordArgument(String name);

  void addArgument(PyExpression arg);
  void addArgumentFirst(PyExpression arg);
  void addArgumentAfter(PyExpression argument, PyExpression afterThis);

  @Nullable
  PyCallExpression getCallExpression();

  /**
   * Tries to map the argument list to callee's idea of parameters.
   * @return a result object with mappings and diagnostic flags.
   */
  AnalysisResult analyzeCall();

  /**
   * Flags to mark analysis results for an argument.
   * Theoretically can be used together, but currently only make sense as a single value per argument.
   */
  enum ArgFlag {
    /** duplicate plain */          IS_DUP,
    /** unexpected */               IS_UNMAPPED,
    /** duplicate **arg */          IS_DUP_KWD,
    /** duplicate *arg */           IS_DUP_TUPLE,
    /** positional past keyword */  IS_POS_PAST_KWD
  }


  /**
   * Result of analysis of argument list application to the callee.
   * Contains neatly arranged lists and mappinga between arguments and parameters,
   * including error diagnostics.
   */
  interface AnalysisResult {

    /**
     * @return A mapping parameter->argument for non-starred parameters (but includes starred argument).
     */
    @NotNull Map<PyExpression, PyParameter> getPlainMappedParams();

    /**
     * @return First *arg, or null.
     */
    @Nullable
    PyStarArgument getTupleArg();

    /**
     * @return A list of parameters mapped to a *arg.
     */
    @NotNull List<PyParameter> getTupleMappedParams();

    /**
     * @return First **arg, or null.
     */
    @Nullable
    PyStarArgument getKwdArg();

    /**
     * @return A list of parameters mapped to an **arg.
     */
    @NotNull List<PyParameter> getKwdMappedParams();

    /**
     * @return A list of parameters for which no arguments were found ('missing').
     */
    @NotNull
    List<PyParameter> getUnmappedParams();


    /**
     * @return Lists all args with their flags.
     * @see ArgFlag
     */
    Map<PyExpression, EnumSet<ArgFlag>> getArgumentFlags();

    /**
     * @return result of a resolveCallee() against the function call to which the paramater list belongs.
     */
    @Nullable
    PyCallExpression.PyMarkedFunction getMarkedFunction();

    PyArgumentList getArgumentList();
  }

}
