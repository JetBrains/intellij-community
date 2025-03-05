// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.ast.PyAstCallExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents an entire call expression, like <tt>foo()</tt> or <tt>foo.bar[1]('x')</tt>.
 */
public interface PyCallExpression extends PyAstCallExpression, PyCallSiteExpression {

  /**
   * @return the expression representing the object being called (reference to a function).
   */
  @Override
  default @Nullable PyExpression getCallee() {
    return (PyExpression)PyAstCallExpression.super.getCallee();
  }

  /**
   * @return the argument list used in the call.
   */
  @Override
  default @Nullable PyArgumentList getArgumentList() {
    return (PyArgumentList)PyAstCallExpression.super.getArgumentList();
  }

  /**
   * @return the argument array used in the call, or an empty array if the call has no argument list.
   */
  @Override
  default PyExpression @NotNull [] getArguments() {
    final PyArgumentList argList = getArgumentList();
    return argList != null ? argList.getArguments() : PyExpression.EMPTY_ARRAY;
  }

  /**
   * Returns the argument if one is present in the list and has appropriate type.
   *
   * @param parameter parameter
   * @param argClass  argument expected type
   * @return the argument expression or null if has wrong type or does not exist
   */
  default @Nullable <T extends PsiElement> T getArgument(@NotNull FunctionParameter parameter, @NotNull Class<T> argClass) {
    final PyArgumentList list = getArgumentList();
    if (list == null) {
      return null;
    }
    return ObjectUtils.tryCast(list.getValueExpressionForParam(parameter), argClass);
  }

  /**
   * Returns the argument marked with the specified keyword, if one is present in the list.
   *
   * @param keyword argument keyword
   * @return the argument or null
   */
  @Override
  default @Nullable PyExpression getKeywordArgument(@NotNull String keyword) {
    return (PyExpression)PyAstCallExpression.super.getKeywordArgument(keyword);
  }

  /**
   * Resolves the callee to possible functions.
   * Try to use {@link PyCallExpression#multiResolveCallee(PyResolveContext)}
   * because resolve result could contain {@code null} callable but {@code non-null} callable type.
   *
   * @param resolveContext resolve context
   * @return the resolved callees or an empty list.
   */
  default @NotNull List<@NotNull PyCallable> multiResolveCalleeFunction(@NotNull PyResolveContext resolveContext) {
    return ContainerUtil.mapNotNull(multiResolveCallee(resolveContext), PyCallableType::getCallable);
  }

  /**
   * Resolves the callee to possible functions.
   *
   * @param resolveContext resolve context
   * @return objects which contains callable, modifier, implicit offset and "implicitly resolved" flag.
   */
  @NotNull
  List<@NotNull PyCallableType> multiResolveCallee(@NotNull PyResolveContext resolveContext);

  /**
   * Resolves the callee to possible functions and maps arguments to parameters for all of them.
   *
   * @param resolveContext resolve context
   * @return objects which contains callable and mappings.
   * Returned list is empty if the callee couldn't be resolved.
   */
  @NotNull
  List<@NotNull PyArgumentsMapping> multiMapArguments(@NotNull PyResolveContext resolveContext);

  class PyArgumentsMapping {
    private final @NotNull PyCallSiteExpression myCallSiteExpression;
    private final @Nullable PyCallableType myCallableType;
    private final @NotNull List<PyCallableParameter> myImplicitParameters;
    private final @NotNull Map<PyExpression, PyCallableParameter> myMappedParameters;
    private final @NotNull List<PyCallableParameter> myUnmappedParameters;
    private final @NotNull List<PyCallableParameter> myUnmappedContainerParameters;
    private final @NotNull List<PyExpression> myUnmappedArguments;
    private final @NotNull List<PyCallableParameter> myParametersMappedToVariadicPositionalArguments;
    private final @NotNull List<PyCallableParameter> myParametersMappedToVariadicKeywordArguments;
    private final @NotNull Map<PyExpression, PyCallableParameter> myMappedTupleParameters;

    public PyArgumentsMapping(@NotNull PyCallSiteExpression callSiteExpression,
                              @Nullable PyCallableType callableType,
                              @NotNull List<PyCallableParameter> implicitParameters,
                              @NotNull Map<PyExpression, PyCallableParameter> mappedParameters,
                              @NotNull List<PyCallableParameter> unmappedParameters,
                              @NotNull List<PyCallableParameter> unmappedContainerParameters,
                              @NotNull List<PyExpression> unmappedArguments,
                              @NotNull List<PyCallableParameter> parametersMappedToVariadicPositionalArguments,
                              @NotNull List<PyCallableParameter> parametersMappedToVariadicKeywordArguments,
                              @NotNull Map<PyExpression, PyCallableParameter> tupleMappedParameters) {
      myCallSiteExpression = callSiteExpression;
      myCallableType = callableType;
      myImplicitParameters = implicitParameters;
      myMappedParameters = mappedParameters;
      myUnmappedParameters = unmappedParameters;
      myUnmappedContainerParameters = unmappedContainerParameters;
      myUnmappedArguments = unmappedArguments;
      myParametersMappedToVariadicPositionalArguments = parametersMappedToVariadicPositionalArguments;
      myParametersMappedToVariadicKeywordArguments = parametersMappedToVariadicKeywordArguments;
      myMappedTupleParameters = tupleMappedParameters;
    }

    public static @NotNull PyArgumentsMapping empty(@NotNull PyCallSiteExpression callSiteExpression) {
      return new PyCallExpression.PyArgumentsMapping(callSiteExpression,
                                                     null,
                                                     Collections.emptyList(),
                                                     Collections.emptyMap(),
                                                     Collections.emptyList(),
                                                     Collections.emptyList(),
                                                     Collections.emptyList(),
                                                     Collections.emptyList(),
                                                     Collections.emptyList(),
                                                     Collections.emptyMap());
    }

    public @NotNull PyCallSiteExpression getCallSiteExpression() {
      return myCallSiteExpression;
    }

    public @Nullable PyCallableType getCallableType() {
      return myCallableType;
    }

    public @NotNull List<PyCallableParameter> getImplicitParameters() {
      return myImplicitParameters;
    }

    public @NotNull Map<PyExpression, PyCallableParameter> getMappedParameters() {
      return myMappedParameters;
    }

    public @NotNull List<PyCallableParameter> getUnmappedParameters() {
      return myUnmappedParameters;
    }

    public @NotNull List<PyCallableParameter> getUnmappedContainerParameters() {
      return myUnmappedContainerParameters;
    }

    public @NotNull List<PyExpression> getUnmappedArguments() {
      return myUnmappedArguments;
    }

    public @NotNull List<PyCallableParameter> getParametersMappedToVariadicPositionalArguments() {
      return myParametersMappedToVariadicPositionalArguments;
    }

    public @NotNull List<PyCallableParameter> getParametersMappedToVariadicKeywordArguments() {
      return myParametersMappedToVariadicKeywordArguments;
    }

    public @NotNull Map<PyExpression, PyCallableParameter> getMappedTupleParameters() {
      return myMappedTupleParameters;
    }

    /**
     * @return true if there are no unmapped parameters and no unmapped arguments, false otherwise
     */
    public boolean isComplete() {
      return getUnmappedParameters().isEmpty() && getUnmappedArguments().isEmpty();
    }
  }
}
