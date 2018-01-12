// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Predicate;
import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.nameResolver.FQNamesProvider;
import com.jetbrains.python.nameResolver.NameResolverTools;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents an entire call expression, like <tt>foo()</tt> or <tt>foo.bar[1]('x')</tt>.
 */
public interface PyCallExpression extends PyCallSiteExpression {

  @Nullable
  @Override
  default PyExpression getReceiver(@Nullable PyCallable resolvedCallee) {
    if (resolvedCallee instanceof PyFunction) {
      final PyFunction function = (PyFunction)resolvedCallee;
      if (function.getModifier() == PyFunction.Modifier.STATICMETHOD) {
        return null;
      }
    }

    final PyExpression callee = getCallee();
    if (callee instanceof PyQualifiedExpression) {
      final PyQualifiedExpression qualifiedCallee = (PyQualifiedExpression)callee;
      final Predicate<String> isConstructorName = name -> PyNames.INIT.equals(name) || PyNames.NEW.equals(name);

      if (resolvedCallee instanceof PyFunction &&
          qualifiedCallee.isQualified() &&
          isConstructorName.apply(resolvedCallee.getName()) &&
          !isConstructorName.apply(qualifiedCallee.getName())) {
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
  @NotNull
  default PyExpression[] getArguments() {
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
  default <T extends PsiElement> T getArgument(int index, @NotNull String keyword, @NotNull Class<T> argClass) {
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
   * <i>Note: the returned list does not contain null values.</i>
   */
  @NotNull
  default List<PyCallable> multiResolveCalleeFunction(@NotNull PyResolveContext resolveContext) {
    return ContainerUtil.mapNotNull(multiResolveCallee(resolveContext, 0), PyMarkedCallee::getElement);
  }

  /**
   * Resolves the callee to possible functions.
   *
   * @param resolveContext resolve context
   * @return objects which contains callable, modifier, implicit offset and "implicitly resolved" flag.
   * <i>Note: the returned list does not contain null values.</i>
   */
  @NotNull
  default List<PyMarkedCallee> multiResolveCallee(@NotNull PyResolveContext resolveContext) {
    return multiResolveCallee(resolveContext, 0);
  }

  /**
   * Resolves the callee to possible functions.
   *
   * @param resolveContext resolve context
   * @param implicitOffset implicit offset which is known from the context
   * @return objects which contains callable, modifier, implicit offset and "implicitly resolved" flag.
   * <i>Note: the returned list does not contain null values.</i>
   */
  @NotNull
  List<PyMarkedCallee> multiResolveCallee(@NotNull PyResolveContext resolveContext, int implicitOffset);

  /**
   * Resolves the callee to possible functions and maps arguments to parameters for all of them.
   *
   * @param resolveContext resolve context
   * @return objects which contains callable and mappings.
   * Returned list is empty if the callee couldn't be resolved.
   * <i>Note: the returned list does not contain null values.</i>
   */
  @NotNull
  default List<PyArgumentsMapping> multiMapArguments(@NotNull PyResolveContext resolveContext) {
    return multiMapArguments(resolveContext, 0);
  }

  /**
   * Resolves the callee to possible functions and maps arguments to parameters for all of them.
   *
   * @param resolveContext resolve context
   * @param implicitOffset implicit offset which is known from the context
   * @return objects which contains callable and mappings.
   * Returned list is empty if the callee couldn't be resolved.
   * <i>Note: the returned list does not contain null values.</i>
   */
  @NotNull
  List<PyArgumentsMapping> multiMapArguments(@NotNull PyResolveContext resolveContext, int implicitOffset);

  /**
   * Checks if the unqualified name of the callee matches any of the specified names
   *
   * @param nameCandidates names to check
   * @return true if matches, false otherwise
   */
  default boolean isCalleeText(@NotNull String... nameCandidates) {
    final PyExpression callee = getCallee();

    return callee instanceof PyReferenceExpression &&
           ContainerUtil.exists(nameCandidates, name -> name.equals(((PyReferenceExpression)callee).getReferencedName()));
  }

  /**
   * Checks if the qualified name of the callee matches any of the specified names provided by provider.
   * May be <strong>heavy</strong>.
   * Use {@link NameResolverTools#isCalleeShortCut(PyCallExpression, FQNamesProvider)}
   * if you can.
   *
   * @param name providers that provides one or more names to check
   * @return true if matches, false otherwise
   * @see com.jetbrains.python.nameResolver
   */
  default boolean isCallee(@NotNull FQNamesProvider... name) {
    final PyExpression callee = getCallee();
    return callee != null && NameResolverTools.isName(callee, name);
  }

  class PyArgumentsMapping {
    @NotNull private final PyCallSiteExpression myCallSiteExpression;
    @Nullable private final PyMarkedCallee myMarkedCallee;
    @NotNull private final List<PyCallableParameter> myImplicitParameters;
    @NotNull private final Map<PyExpression, PyCallableParameter> myMappedParameters;
    @NotNull private final List<PyCallableParameter> myUnmappedParameters;
    @NotNull private final List<PyExpression> myUnmappedArguments;
    @NotNull private final List<PyCallableParameter> myParametersMappedToVariadicPositionalArguments;
    @NotNull private final List<PyCallableParameter> myParametersMappedToVariadicKeywordArguments;
    @NotNull private final Map<PyExpression, PyCallableParameter> myMappedTupleParameters;

    public PyArgumentsMapping(@NotNull PyCallSiteExpression callSiteExpression,
                              @Nullable PyMarkedCallee markedCallee,
                              @NotNull List<PyCallableParameter> implicitParameters,
                              @NotNull Map<PyExpression, PyCallableParameter> mappedParameters,
                              @NotNull List<PyCallableParameter> unmappedParameters,
                              @NotNull List<PyExpression> unmappedArguments,
                              @NotNull List<PyCallableParameter> parametersMappedToVariadicPositionalArguments,
                              @NotNull List<PyCallableParameter> parametersMappedToVariadicKeywordArguments,
                              @NotNull Map<PyExpression, PyCallableParameter> tupleMappedParameters) {
      myCallSiteExpression = callSiteExpression;
      myMarkedCallee = markedCallee;
      myImplicitParameters = implicitParameters;
      myMappedParameters = mappedParameters;
      myUnmappedParameters = unmappedParameters;
      myUnmappedArguments = unmappedArguments;
      myParametersMappedToVariadicPositionalArguments = parametersMappedToVariadicPositionalArguments;
      myParametersMappedToVariadicKeywordArguments = parametersMappedToVariadicKeywordArguments;
      myMappedTupleParameters = tupleMappedParameters;
    }

    /**
     * @deprecated
     * Use {@link #PyArgumentsMapping(PyCallSiteExpression, PyMarkedCallee, List, Map, List, List, List, List, Map)} that includes
     * implicitly mapped parameters. This constructor will be removed in 2018.2.
     */
    @Deprecated
    public PyArgumentsMapping(@NotNull PyCallSiteExpression callSiteExpression,
                              @Nullable PyMarkedCallee markedCallee,
                              @NotNull Map<PyExpression, PyCallableParameter> mappedParameters,
                              @NotNull List<PyCallableParameter> unmappedParameters,
                              @NotNull List<PyExpression> unmappedArguments,
                              @NotNull List<PyCallableParameter> parametersMappedToVariadicPositionalArguments,
                              @NotNull List<PyCallableParameter> parametersMappedToVariadicKeywordArguments,
                              @NotNull Map<PyExpression, PyCallableParameter> tupleMappedParameters) {
      this(callSiteExpression, markedCallee, Collections.emptyList(), mappedParameters, unmappedParameters, unmappedArguments,
           parametersMappedToVariadicPositionalArguments, parametersMappedToVariadicKeywordArguments, tupleMappedParameters);
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
    public PyMarkedCallee getMarkedCallee() {
      return myMarkedCallee;
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

  /**
   * Couples function with a flag describing the way it is called.
   */
  class PyMarkedCallee extends RatedResolveResult {
    @NotNull private final PyCallableType myCallableType;
    @Nullable private final PyFunction.Modifier myModifier;
    private final int myImplicitOffset;
    private final boolean myImplicitlyResolved;

    /**
     * Method-oriented constructor.
     *
     * @param callableType       type describing callable object
     * @param function           the method (or any other callable, but why bother then).
     * @param modifier           classmethod or staticmethod modifier
     * @param offset             implicit argument offset; parameters up to this are implicitly filled in the call.
     * @param implicitlyResolved value for {@link #isImplicitlyResolved()}
     * @param rate               callee rate
     */
    public PyMarkedCallee(@NotNull PyCallableType callableType,
                          @Nullable PyCallable function,
                          @Nullable PyFunction.Modifier modifier,
                          int offset,
                          boolean implicitlyResolved,
                          int rate) {
      super(rate, function);
      myCallableType = callableType;
      myModifier = modifier;
      myImplicitOffset = offset;
      myImplicitlyResolved = implicitlyResolved;
    }

    @NotNull
    public PyCallableType getCallableType() {
      return myCallableType;
    }

    @Override
    @Nullable
    public PyCallable getElement() {
      return (PyCallable)super.getElement();
    }

    @Nullable
    public PyFunction.Modifier getModifier() {
      return myModifier;
    }

    /**
     * @return number of implicitly passed positional parameters; 0 means no parameters are passed implicitly.
     * Note that a <tt>*args</tt> is never marked as passed implicitly.
     * E.g. for a function like <tt>foo(a, b, *args)</tt> always holds <tt>getImplicitOffset() < 2</tt>.
     */
    public int getImplicitOffset() {
      return myImplicitOffset;
    }

    /**
     * @return true iff the result is resolved based on divination and name similarity rather than by proper resolution process.
     */
    public boolean isImplicitlyResolved() {
      return myImplicitlyResolved;
    }
  }
}
