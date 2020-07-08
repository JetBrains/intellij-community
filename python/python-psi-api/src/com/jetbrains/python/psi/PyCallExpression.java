// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.nameResolver.FQNamesProvider;
import com.jetbrains.python.nameResolver.NameResolverTools;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Represents an entire call expression, like <tt>foo()</tt> or <tt>foo.bar[1]('x')</tt>.
 */
public interface PyCallExpression extends PyCallSiteExpression {

  @Nullable
  @Override
  default PyExpression getReceiver(@Nullable PyCallable resolvedCallee) {
    if (resolvedCallee instanceof PyFunction) {
      final PyFunction function = (PyFunction)resolvedCallee;
      if (!PyNames.NEW.equals(function.getName()) && function.getModifier() == PyFunction.Modifier.STATICMETHOD) {
        return null;
      }
    }

    final PyExpression callee = getCallee();
    if (callee instanceof PyQualifiedExpression) {
      final PyQualifiedExpression qualifiedCallee = (PyQualifiedExpression)callee;
      final Predicate<String> isConstructorName = name -> PyNames.INIT.equals(name) || PyNames.NEW.equals(name);

      if (resolvedCallee instanceof PyFunction &&
          qualifiedCallee.isQualified() &&
          isConstructorName.test(resolvedCallee.getName()) &&
          !isConstructorName.test(qualifiedCallee.getName())) {
        return null;
      }

      if (resolvedCallee != null && PyNames.CALL.equals(resolvedCallee.getName()) && !PyNames.CALL.equals(qualifiedCallee.getName())) {
        return qualifiedCallee;
      }

      return qualifiedCallee.getQualifier();
    }

    return null;
  }

  @NotNull
  @Override
  default List<PyExpression> getArguments(@Nullable PyCallable resolvedCallee) {
    return Arrays.asList(getArguments());
  }

  /**
   * @return the expression representing the object being called (reference to a function).
   */
  @Nullable
  PyExpression getCallee();

  /**
   * @return the argument list used in the call.
   */
  @Nullable
  default PyArgumentList getArgumentList() {
    return PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
  }

  /**
   * @return the argument array used in the call, or an empty array if the call has no argument list.
   */
  default PyExpression @NotNull [] getArguments() {
    final PyArgumentList argList = getArgumentList();
    return argList != null ? argList.getArguments() : PyExpression.EMPTY_ARRAY;
  }

  /**
   * If the list of arguments has at least {@code index} elements and the {@code index}'th element is of type {@code argClass},
   * returns it. Otherwise, returns null.
   *
   * @param index    argument index
   * @param argClass argument expected type
   * @return the argument or null
   */
  @Nullable
  default <T extends PsiElement> T getArgument(int index, @NotNull Class<T> argClass) {
    final PyExpression[] args = getArguments();
    return args.length > index ? ObjectUtils.tryCast(args[index], argClass) : null;
  }

  /**
   * Returns the argument marked with the specified keyword or the argument at the specified position, if one is present in the list.
   *
   * @param index    argument index
   * @param keyword  argument keyword
   * @param argClass argument expected type
   * @return the argument or null
   */
  @Nullable
  default <T extends PsiElement> T getArgument(int index, @NonNls @NotNull String keyword, @NotNull Class<T> argClass) {
    final PyExpression arg = getKeywordArgument(keyword);
    if (arg != null) {
      return ObjectUtils.tryCast(arg, argClass);
    }
    return getArgument(index, argClass);
  }

  /**
   * Returns the argument if one is present in the list and has appropriate type.
   *
   * @param parameter parameter
   * @param argClass  argument expected type
   * @return the argument expression or null if has wrong type or does not exist
   */
  @Nullable
  default <T extends PsiElement> T getArgument(@NotNull FunctionParameter parameter, @NotNull Class<T> argClass) {
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
  @Nullable
  default PyExpression getKeywordArgument(@NotNull String keyword) {
    for (PyExpression arg : getArguments()) {
      if (arg instanceof PyKeywordArgument) {
        final PyKeywordArgument keywordArg = (PyKeywordArgument)arg;
        if (keyword.equals(keywordArg.getKeyword())) {
          return keywordArg.getValueExpression();
        }
      }
    }
    return null;
  }

  /**
   * Resolves the callee to possible functions.
   * Try to use {@link PyCallExpression#multiResolveCallee(PyResolveContext)}
   * because resolve result could contain {@code null} callable but {@code non-null} callable type.
   *
   * @param resolveContext resolve context
   * @return the resolved callees or an empty list.
   */
  @NotNull
  default List<@NotNull PyCallable> multiResolveCalleeFunction(@NotNull PyResolveContext resolveContext) {
    return ContainerUtil.mapNotNull(multiResolveCallee(resolveContext), PyCallableType::getCallable);
  }

  /**
   * Resolves the callee to possible functions.
   *
   * @param resolveContext resolve context
   * @return objects which contains callable, modifier, implicit offset and "implicitly resolved" flag.
   * @apiNote This method will become abstract in 2021.1.
   */
  @NotNull
  default List<@NotNull PyCallableType> multiResolveCallee(@NotNull PyResolveContext resolveContext) {
    return multiResolveCallee(resolveContext, 0);
  }

  /**
   * Resolves the callee to possible functions.
   *
   * @param resolveContext resolve context
   * @param implicitOffset implicit offset which is known from the context
   * @return objects which contains callable, modifier, implicit offset and "implicitly resolved" flag.
   * @deprecated {@code implicitOffset} is no longer processed, use {@link PyCallExpression#multiResolveCallee(PyResolveContext)} instead.
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  List<@NotNull PyCallableType> multiResolveCallee(@NotNull PyResolveContext resolveContext, int implicitOffset);

  /**
   * Resolves the callee to possible functions and maps arguments to parameters for all of them.
   *
   * @param resolveContext resolve context
   * @return objects which contains callable and mappings.
   * Returned list is empty if the callee couldn't be resolved.
   * @apiNote This method will become abstract in 2021.1.
   */
  @NotNull
  default List<@NotNull PyArgumentsMapping> multiMapArguments(@NotNull PyResolveContext resolveContext) {
    return multiMapArguments(resolveContext, 0);
  }

  /**
   * Resolves the callee to possible functions and maps arguments to parameters for all of them.
   *
   * @param resolveContext resolve context
   * @param implicitOffset implicit offset which is known from the context
   * @return objects which contains callable and mappings.
   * Returned list is empty if the callee couldn't be resolved.
   * @deprecated {@code implicitOffset} is no longer processed, use {@link PyCallExpression#multiMapArguments(PyResolveContext)} instead.
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  List<@NotNull PyArgumentsMapping> multiMapArguments(@NotNull PyResolveContext resolveContext, int implicitOffset);

  /**
   * Checks if the unqualified name of the callee matches any of the specified names
   *
   * @param nameCandidates names to check
   * @return true if matches, false otherwise
   */
  default boolean isCalleeText(String @NotNull ... nameCandidates) {
    final PyExpression callee = getCallee();

    return callee instanceof PyReferenceExpression &&
           ContainerUtil.exists(nameCandidates, name -> name.equals(((PyReferenceExpression)callee).getReferencedName()));
  }

  /**
   * Checks if the qualified name of the callee matches any of the specified names provided by provider.
   * May be <strong>heavy</strong>, and it is not recommended to use.
   * Use {@link NameResolverTools#isCalleeShortCut(PyCallExpression, FQNamesProvider...)} or
   * {@link com.jetbrains.extensions.python.PyCallExpressionExtKt#isCalleeName(PyCallExpression, FQNamesProvider...)}.
   *
   * @param name providers that provides one or more names to check
   * @return true if matches, false otherwise
   * @see com.jetbrains.python.nameResolver
   * @see com.jetbrains.extensions.python.PyCallExpressionExtKt#isCalleeName(PyCallExpression, FQNamesProvider...)
   * @deprecated use {@link com.jetbrains.extensions.python.PyCallExpressionExtKt#isCalleeName(PyCallExpression, FQNamesProvider...)}.
   */
  @Deprecated
  default boolean isCallee(FQNamesProvider @NotNull ... name) {
    final PyExpression callee = getCallee();
    return callee instanceof PyReferenceExpression && NameResolverTools.isName(callee, name);
  }

  class PyArgumentsMapping {
    @NotNull private final PyCallSiteExpression myCallSiteExpression;
    @Nullable private final PyCallableType myCallableType;
    @NotNull private final List<PyCallableParameter> myImplicitParameters;
    @NotNull private final Map<PyExpression, PyCallableParameter> myMappedParameters;
    @NotNull private final List<PyCallableParameter> myUnmappedParameters;
    @NotNull private final List<PyExpression> myUnmappedArguments;
    @NotNull private final List<PyCallableParameter> myParametersMappedToVariadicPositionalArguments;
    @NotNull private final List<PyCallableParameter> myParametersMappedToVariadicKeywordArguments;
    @NotNull private final Map<PyExpression, PyCallableParameter> myMappedTupleParameters;

    public PyArgumentsMapping(@NotNull PyCallSiteExpression callSiteExpression,
                              @Nullable PyCallableType callableType,
                              @NotNull List<PyCallableParameter> implicitParameters,
                              @NotNull Map<PyExpression, PyCallableParameter> mappedParameters,
                              @NotNull List<PyCallableParameter> unmappedParameters,
                              @NotNull List<PyExpression> unmappedArguments,
                              @NotNull List<PyCallableParameter> parametersMappedToVariadicPositionalArguments,
                              @NotNull List<PyCallableParameter> parametersMappedToVariadicKeywordArguments,
                              @NotNull Map<PyExpression, PyCallableParameter> tupleMappedParameters) {
      myCallSiteExpression = callSiteExpression;
      myCallableType = callableType;
      myImplicitParameters = implicitParameters;
      myMappedParameters = mappedParameters;
      myUnmappedParameters = unmappedParameters;
      myUnmappedArguments = unmappedArguments;
      myParametersMappedToVariadicPositionalArguments = parametersMappedToVariadicPositionalArguments;
      myParametersMappedToVariadicKeywordArguments = parametersMappedToVariadicKeywordArguments;
      myMappedTupleParameters = tupleMappedParameters;
    }

    @NotNull
    public static PyArgumentsMapping empty(@NotNull PyCallSiteExpression callSiteExpression) {
      return new PyCallExpression.PyArgumentsMapping(callSiteExpression,
                                                     null,
                                                     Collections.emptyList(),
                                                     Collections.emptyMap(),
                                                     Collections.emptyList(),
                                                     Collections.emptyList(),
                                                     Collections.emptyList(),
                                                     Collections.emptyList(),
                                                     Collections.emptyMap());
    }

    @NotNull
    public PyCallSiteExpression getCallSiteExpression() {
      return myCallSiteExpression;
    }

    @Nullable
    public PyCallableType getCallableType() {
      return myCallableType;
    }

    @NotNull
    public List<PyCallableParameter> getImplicitParameters() {
      return myImplicitParameters;
    }

    @NotNull
    public Map<PyExpression, PyCallableParameter> getMappedParameters() {
      return myMappedParameters;
    }

    @NotNull
    public List<PyCallableParameter> getUnmappedParameters() {
      return myUnmappedParameters;
    }

    @NotNull
    public List<PyExpression> getUnmappedArguments() {
      return myUnmappedArguments;
    }

    @NotNull
    public List<PyCallableParameter> getParametersMappedToVariadicPositionalArguments() {
      return myParametersMappedToVariadicPositionalArguments;
    }

    @NotNull
    public List<PyCallableParameter> getParametersMappedToVariadicKeywordArguments() {
      return myParametersMappedToVariadicKeywordArguments;
    }

    @NotNull
    public Map<PyExpression, PyCallableParameter> getMappedTupleParameters() {
      return myMappedTupleParameters;
    }
  }
}
