/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.ast.PyAstSingleStarParameter;
import com.jetbrains.python.ast.PyAstSlashParameter;
import com.jetbrains.python.ast.impl.ParamHelperCore;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarTupleType;
import com.jetbrains.python.psi.types.PyUnpackedTypedDictType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameter-related things that should not belong directly to PyParameter.
 */
public final class ParamHelper {

  private ParamHelper() {
  }

  /**
   * Runs a {@link ParamWalker ParamWalker} down the array of parameters, recursively descending into tuple parameters.
   * If the array is from PyParamaterList.getParameters(), parameters are visited in the order of textual appearance
   *
   * @param params where to walk
   * @param walker the walker with callbacks.
   */
  public static void walkDownParamArray(PyParameter[] params, ParamWalker walker) {
    walkDownParameters(ContainerUtil.map(params, PyCallableParameterImpl::psi), walker);
  }

  public static void walkDownParameters(@NotNull List<? extends PyCallableParameter> parameters, @NotNull ParamWalker walker) {
    int i = 0;
    for (PyCallableParameter parameter : parameters) {
      final PyParameter psi = parameter.getParameter();
      final boolean first = i == 0;
      final boolean last = i == parameters.size() - 1;

      if (psi instanceof PyTupleParameter tupleParameter) {
        walker.enterTupleParameter(tupleParameter, first, last);
        walkDownParamArray(tupleParameter.getContents(), walker);
        walker.leaveTupleParameter(tupleParameter, first, last);
      }
      else if (psi instanceof PyNamedParameter) {
        walker.visitNamedParameter((PyNamedParameter)psi, first, last);
      }
      else if (psi instanceof PySlashParameter) {
        walker.visitSlashParameter((PySlashParameter)psi, first, last);
      }
      else if (psi instanceof PySingleStarParameter) {
        walker.visitSingleStarParameter((PySingleStarParameter)psi, first, last);
      }
      else if (parameter.isPositionOnlySeparator()) {
        walker.visitSlashParameter(null, first, last);
      }
      else if (parameter.isKeywordOnlySeparator()) {
        walker.visitSingleStarParameter(null, first, last);
      }
      else {
        walker.visitNonPsiParameter(parameter, first, last);
      }
      i++;
    }
  }

  public static @NotNull String getPresentableText(PyParameter @NotNull [] parameters,
                                                   boolean includeDefaultValue,
                                                   @Nullable TypeEvalContext context) {
    return getPresentableText(ContainerUtil.map(parameters, PyCallableParameterImpl::psi), includeDefaultValue, context);
  }

  public static @NotNull String getPresentableText(@NotNull List<? extends PyCallableParameter> parameters,
                                                   boolean includeDefaultValue,
                                                   @Nullable TypeEvalContext context) {
    final StringBuilder result = new StringBuilder();
    result.append("(");

    walkDownParameters(
      parameters,
      new ParamHelper.ParamWalker() {
        @Override
        public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          result.append("(");
        }

        @Override
        public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          result.append(")");
          if (!last) result.append(", ");
        }

        @Override
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          visitNonPsiParameter(PyCallableParameterImpl.psi(param), first, last);
        }

        @Override
        public void visitSlashParameter(@Nullable PySlashParameter param, boolean first, boolean last) {
          result.append(PyAstSlashParameter.TEXT);
          if (!last) result.append(", ");
        }

        @Override
        public void visitSingleStarParameter(@Nullable PySingleStarParameter param, boolean first, boolean last) {
          result.append(PyAstSingleStarParameter.TEXT);
          if (!last) result.append(", ");
        }

        @Override
        public void visitNonPsiParameter(@NotNull PyCallableParameter parameter, boolean first, boolean last) {
          result.append(parameter.getPresentableText(includeDefaultValue, context));
          if (!last) result.append(", ");
        }
      }
    );

    result.append(")");
    return result.toString();
  }

  public static @NotNull String getNameInSignature(@NotNull PyCallableParameter parameter) {
    final StringBuilder sb = new StringBuilder();

    if (parameter.isPositionalContainer()) {
      sb.append("*");
    }
    else if (parameter.isKeywordContainer()) sb.append("**");

    final String name = parameter.getName();
    sb.append(name != null ? name : "...");

    return sb.toString();
  }

  public static @NotNull String getNameInSignature(@NotNull PyNamedParameter parameter) {
    return ParamHelperCore.getNameInSignature(parameter);
  }

  /**
   * @param defaultValue             string returned by {@link PyCallableParameter#getDefaultValueText()} or {@link PyParameter#getDefaultValueText()}.
   * @param parameterRenderedAsTyped true if parameter is rendered with type annotation.
   * @return equal sign (surrounded with spaces if {@code parameterRenderedAsTyped}) +
   * {@code defaultValue} (with body escaped if it is a string literal)
   */
  @Contract("null, _->null")
  public static @Nullable String getDefaultValuePartInSignature(@Nullable String defaultValue, boolean parameterRenderedAsTyped) {
    return ParamHelperCore.getDefaultValuePartInSignature(defaultValue, parameterRenderedAsTyped);
  }

  public static boolean couldHaveDefaultValue(@NotNull String parameterName) {
    return !parameterName.startsWith("*") && !parameterName.equals(PyAstSlashParameter.TEXT);
  }

  public static boolean isSelfArgsKwargsCallable(@Nullable PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyCallable) {
      final List<PyCallableParameter> parameters = ((PyCallable)element).getParameters(context);
      return parameters.size() == 3 &&
             parameters.get(0).isSelf() &&
             parameters.get(1).isPositionalContainer() &&
             parameters.get(2).isKeywordContainer();
    }

    return false;
  }

  public static boolean isSelfArgsKwargsCallable(@NotNull PyCallableType type, @NotNull TypeEvalContext context) {
    final List<PyCallableParameter> parameters = type.getParameters(context);
    return parameters != null && isSelfArgsKwargsSignature(parameters);
  }

  public static boolean isSelfArgsKwargsSignature(@NotNull List<PyCallableParameter> parameters) {
    return parameters.size() == 3 &&
           parameters.get(0).isSelf() &&
           parameters.get(1).isPositionalContainer() &&
           parameters.get(2).isKeywordContainer();
  }

  public static boolean isArgsKwargsCallable(@NotNull PyCallableType type, @NotNull TypeEvalContext context) {
    final List<PyCallableParameter> parameters = type.getParameters(context);
    return parameters != null && isArgsKwargsSignature(parameters);
  }

  public static boolean isArgsKwargsSignature(@NotNull List<PyCallableParameter> parameters) {
    return parameters.size() == 2 &&
           parameters.get(0).isPositionalContainer() &&
           parameters.get(1).isKeywordContainer();
  }

  /**
   * Checks if the given list of parameters represents a so-called wildcard signature.
   * A wildcard signature contains only untyped *args and **kwargs (or *args: Any, **kwargs: Any),
   * possibly with a self parameter for methods.
   *
   * @see <a href="https://typing.python.org/en/latest/spec/callables.html#meaning-of-in-callable">Meaning of ... in Callable</a>
   */
  public static boolean isWildcardSignature(@NotNull List<PyCallableParameter> parameters, @NotNull TypeEvalContext context) {
    var params = dropSelf(parameters);

    if (params.isEmpty()) return false;

    return params.size() == 2 &&
           params.getFirst().isPositionalContainer() &&
           params.getLast().isKeywordContainer() &&
           params.getFirst().getArgumentType(context) == null &&
           params.getLast().getArgumentType(context) == null;
  }

  /**
   * Removes the 'self' parameter from the beginning of the parameter list if it exists.
   *
   * @param parameters the list of {@link PyCallableParameter} to process
   * @return a list of {@link PyCallableParameter} with the 'self' parameter removed,
   *         or the same list if the first parameter is not 'self'.
   */
  public static List<PyCallableParameter> dropSelf(@NotNull List<PyCallableParameter> parameters) {
    return !parameters.isEmpty() && parameters.getFirst().isSelf() ? parameters.subList(1, parameters.size()) : parameters;
  }

  /**
   * Processes a list of callable parameters, expanding container parameters
   * into their unpacked forms if applicable.
   * @see #unpackPositionalContainerParameters(List, TypeEvalContext)
   * @see #unpackKeywordContainerParameters(List, TypeEvalContext)
   *
   * @return a list of {@link PyCallableParameter} with container parameters expanded.
   */
  public static @NotNull List<PyCallableParameter> unpackContainerParameters(@NotNull List<PyCallableParameter> parameters,
                                                                             @NotNull TypeEvalContext context) {
    return unpackPositionalContainerParameters(unpackKeywordContainerParameters(parameters, context), context);
  }


  /**
   * Processes a list of callable parameters, expanding any keyword container parameters
   * into their unpacked forms if applicable.
   *
   * @param parameters the list of callable parameters to process. Each parameter is expected
   *                   to implement {@link PyCallableParameter}.
   * @param context    the type evaluation context to use when resolving parameter types.
   * @return a list of callable parameters where any keyword container parameters are replaced
   *         with their unpacked representations.
   */
  public static @NotNull List<PyCallableParameter> unpackKeywordContainerParameters(@NotNull List<PyCallableParameter> parameters,
                                                                                    @NotNull TypeEvalContext context) {
    return StreamEx.of(parameters)
      .flatMap(param -> {
        if (param.isKeywordContainer()) {
          PyType paramType = param.getType(context);
          if (paramType instanceof PyUnpackedTypedDictType unpackedTypedDictType) {
            return StreamEx.of(unpackedTypedDictType.getUnpackedParameters(context));
          }
        }
        return StreamEx.of(param);
      }).toList();
  }

  /**
   * Processes a list of callable parameters, expanding any positional container parameters
   * annotated with a bound tuple type into individual positional-only parameters.
   * <p>
   * For example, {@code *args: *tuple[int, *tuple[str, ...], float]} is expanded into
   * {@code __p0: int, *args: str, __p1: float}.
   * <p>
   * Expansion is skipped for unbound tuples ({@code *tuple[str, ...]}) and tuples containing
   * {@link PyTypeVarTupleType} elements.
   *
   * @return a list with positional containers expanded into individual parameters where applicable
   */
  public static @NotNull List<PyCallableParameter> unpackPositionalContainerParameters(
    @NotNull List<PyCallableParameter> parameters,
    @NotNull TypeEvalContext context
  ) {
    return StreamEx.of(parameters)
      .flatMap(param -> {
        if (!param.isPositionalContainer()) return StreamEx.of(param);

        PyType paramType = param.getType(context);
        if (!(paramType instanceof PyTupleType tupleType)) return StreamEx.of(param);

        List<PyCallableParameter> expanded = tupleType.asUnpackedTupleType().asCallableParameters();
        return expanded.isEmpty() ? StreamEx.of(param) : StreamEx.of(expanded);
      }).toList();
  }

  public interface ParamWalker {
    /**
     * Is called when a tuple parameter is encountered, before visiting any parameters nested in it.
     *
     * @param param the parameter
     * @param first true iff it is the first in the list
     * @param last  true it is the last in the list
     */
    void enterTupleParameter(PyTupleParameter param, boolean first, boolean last);

    /**
     * Is called when all nested parameters of a given tuple parameter are visited.
     *
     * @param param the parameter
     * @param first true iff it is the first in the list
     * @param last  true it is the last in the list
     */
    void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last);

    /**
     * Is called when a named parameter is encountered.
     *
     * @param param the parameter
     * @param first true iff it is the first in the list
     * @param last  true it is the last in the list
     */
    void visitNamedParameter(PyNamedParameter param, boolean first, boolean last);

    /**
     * Is called when a positional-only separator ({@code /}) is encountered.
     *
     * @param param the underlying PSI element, or {@code null} if the separator is synthetic
     *              (e.g. created via {@link PyCallableParameterImpl#positionalOnlySeparatorNonPsi()})
     */
    void visitSlashParameter(@Nullable PySlashParameter param, boolean first, boolean last);

    /**
     * Is called when a keyword-only separator ({@code *}) is encountered.
     *
     * @param param the underlying PSI element, or {@code null} if the separator is synthetic
     *              (e.g. created via {@link PyCallableParameterImpl#keywordOnlySeparatorNonPsi()})
     */
    void visitSingleStarParameter(@Nullable PySingleStarParameter param, boolean first, boolean last);

    void visitNonPsiParameter(@NotNull PyCallableParameter parameter, boolean first, boolean last);
  }

  public abstract static class ParamVisitor implements ParamWalker {

    @Override
    public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) { }

    @Override
    public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) { }

    @Override
    public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) { }

    @Override
    public void visitSlashParameter(@Nullable PySlashParameter param, boolean first, boolean last) { }

    @Override
    public void visitSingleStarParameter(@Nullable PySingleStarParameter param, boolean first, boolean last) { }

    @Override
    public void visitNonPsiParameter(@NotNull PyCallableParameter parameter, boolean first, boolean last) { }
  }

  public static List<PyNamedParameter> collectNamedParameters(PyParameterList plist) {
    final List<PyNamedParameter> result = new ArrayList<>(10); // a random 'enough'
    walkDownParamArray(
      plist.getParameters(),
      new ParamVisitor() {
        @Override
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          result.add(param);
        }
      }
    );
    return result;
  }

  /**
   * Returns the argument type from {@code expectedParams} that a callable type expects for a
   * parameter at positional index {@code paramPos} in a function definition, respecting variadic
   * {@code *args} parameters, position-only separators ({@code /}), and keyword-only separators
   * ({@code *}).
   *
   * @param paramPos      0-based index of the parameter within the function's non-self named parameters
   * @param expectedParams explicit (non-implicit) parameters of the callable type
   */
  @ApiStatus.Experimental
  public static @Nullable PyType getExpectedTypeForPositionalParam(int paramPos,
                                                                   @NotNull List<PyCallableParameter> expectedParams,
                                                                   @NotNull TypeEvalContext context) {
    int expectedPos = 0;
    for (PyCallableParameter expectedParam : expectedParams) {
      if (expectedParam.isPositionalContainer()) {
        return paramPos >= expectedPos ? expectedParam.getArgumentType(context) : null;
      }
      if (expectedParam.isKeywordContainer() || expectedParam.isKeywordOnlySeparator()) {
        break;
      }
      if (!expectedParam.isPositionOnlySeparator()) {
        if (expectedPos == paramPos) {
          return expectedParam.getArgumentType(context);
        }
        expectedPos++;
      }
    }
    return null;
  }
}
