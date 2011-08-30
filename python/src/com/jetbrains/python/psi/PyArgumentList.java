package com.jetbrains.python.psi;

import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents an argument list of a function call.
 *
 * @author yole
 */
public interface PyArgumentList extends PyElement {

  @NotNull PyExpression[] getArguments();

  @Nullable PyKeywordArgument getKeywordArgument(String name);

  void addArgument(PyExpression arg);
  void addArgumentFirst(PyExpression arg);
  void addArgumentAfter(PyExpression argument, PyExpression afterThis);

  /**
   * @return the call expression to which this argument list belongs; not null in correctly parsed cases.
   */
  @Nullable
  PyCallExpression getCallExpression();

  /**
   * Tries to map the argument list to callee's idea of parameters.
   * @return a result object with mappings and diagnostic flags.
   * @param resolveContext the reference resolution context
   */
  @NotNull
  AnalysisResult analyzeCall(PyResolveContext resolveContext);

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


  /**
   * Result of analysis of argument list application to the callee.
   * Contains neatly arranged lists and mappings between arguments and parameters,
   * including error diagnostics.
   */
  interface AnalysisResult {
    boolean isImplicitlyResolved();

    /**
     * @return A mapping argument->parameter for non-starred parameters (but includes starred argument).
     */
    @NotNull Map<PyExpression, PyNamedParameter> getPlainMappedParams();

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
     * This case is rare, so a separate method is introduced, to keep {@link AnalysisResult#getPlainMappedParams()} simple.
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
     * @see ArgFlag
     */
    Map<PyExpression, EnumSet<ArgFlag>> getArgumentFlags();

    /**
     * @return result of a resolveCallee() against the function call to which the parameter list belongs.
     */
    @Nullable
    PyCallExpression.PyMarkedCallee getMarkedCallee();

    PyArgumentList getArgumentList();
  }

}
