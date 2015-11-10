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

import com.intellij.psi.PsiElement;
import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.nameResolver.FQNamesProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Represents an entire call expression, like <tt>foo()</tt> or <tt>foo.bar[1]('x')</tt>.
 */
public interface PyCallExpression extends PyCallSiteExpression {

  /**
   * @return the expression representing the object being called (reference to a function).
   */
  @Nullable
  PyExpression getCallee();

  /**
   * @return ArgumentList used in the call.
   */
  @Nullable
  PyArgumentList getArgumentList();

  /**
   * @return The array of call arguments, or an empty array if the call has no argument list.
   */
  @NotNull
  PyExpression[] getArguments();

  /**
   * If the list of arguments has at least {@code index} elements and the index'th element is of type argClass,
   * returns it. Otherwise, returns null.
   *
   * @param index    argument index
   * @param argClass argument expected type
   * @return the argument or null
   */
  @Nullable
  <T extends PsiElement> T getArgument(int index, Class<T> argClass);

  /**
   * Returns the argument at the specified position or the argument marked with the specified keyword, if one is present in the list.
   *
   * @param index    argument index
   * @param keyword  the argument keyword
   * @param argClass argument expected type
   * @return the argument or null
   */
  @Nullable
  <T extends PsiElement> T getArgument(int index, String keyword, Class<T> argClass);

  /**
   * Returns the argument if one is present in the list.
   *
   * @param parameter parameter
   * @param argClass  argument expected type
   * @return the argument or null
   */
  @Nullable
  <T extends PsiElement> T getArgument(@NotNull final FunctionParameter parameter, @NotNull Class<T> argClass);

  @Nullable
  PyExpression getKeywordArgument(String keyword);

  /**
   * TODO: Copy/Paste with {@link PyArgumentList#addArgument(PyExpression)}
   *
   * @param expression
   */
  void addArgument(PyExpression expression);

  /**
   * Resolves callee down to particular function (standalone, method, or constructor).
   * Return's function part contains a function, never null.
   * Return's flag part marks the particulars of the call, esp. the implicit first arg situation.
   * Return is null if callee cannot be resolved.
   *
   * @param resolveContext the reference resolve context
   */
  @Nullable
  PyMarkedCallee resolveCallee(PyResolveContext resolveContext);

  /**
   * Resolves callee down to particular function (standalone, method, or constructor).
   * Return is null if callee cannot be resolved.
   *
   * @param resolveContext the reference resolve context
   */
  @Nullable
  PyCallable resolveCalleeFunction(PyResolveContext resolveContext);

  /**
   * @param resolveContext the reference resolve context
   * @param implicitOffset known from the context implicit offset
   */
  @Nullable
  PyMarkedCallee resolveCallee(PyResolveContext resolveContext, int implicitOffset);

  @NotNull
  PyArgumentsMapping mapArguments(@NotNull PyResolveContext resolveContext);

  @NotNull
  PyArgumentsMapping mapArguments(@NotNull PyResolveContext resolveContext, int implicitOffset);

  /**
   * Checks if the unqualified name of the callee matches any of the specified names
   *
   * @param nameCandidates the names to check
   * @return true if matches, false otherwise
   */
  boolean isCalleeText(@NotNull String... nameCandidates);

  /**
   * Checks if the qualified name of the callee matches any of the specified names provided by provider.
   * May be <strong>heavy</strong>.
   * Use {@link com.jetbrains.python.nameResolver.NameResolverTools#isCalleeShortCut(PyCallExpression, FQNamesProvider)}
   * if you can.
   *
   * @param name providers that provides one or more names to check
   * @return true if matches, false otherwise
   * @see com.jetbrains.python.nameResolver
   */
  boolean isCallee(@NotNull FQNamesProvider... name);

  class PyArgumentsMapping {
    @NotNull private final PyCallExpression myCallExpression;
    @Nullable private final PyMarkedCallee myCallee;
    @NotNull private final Map<PyExpression, PyNamedParameter> myMappedParameters;
    @NotNull private final List<PyParameter> myUnmappedParameters;
    @NotNull private final List<PyExpression> myUnmappedArguments;
    @NotNull private final List<PyNamedParameter> myParametersMappedToVariadicPositionalArguments;
    @NotNull private final List<PyNamedParameter> myParametersMappedToVariadicKeywordArguments;
    @NotNull private final Map<PyExpression, PyTupleParameter> myMappedTupleParameters;

    public PyArgumentsMapping(@NotNull PyCallExpression expression,
                              @Nullable PyMarkedCallee markedCallee,
                              @NotNull Map<PyExpression, PyNamedParameter> mappedParameters,
                              @NotNull List<PyParameter> unmappedParameters,
                              @NotNull List<PyExpression> unmappedArguments,
                              @NotNull List<PyNamedParameter> parametersMappedToVariadicPositionalArguments,
                              @NotNull List<PyNamedParameter> parametersMappedToVariadicKeywordArguments,
                              @NotNull Map<PyExpression, PyTupleParameter> tupleMappedParameters) {
      myCallExpression = expression;
      myCallee = markedCallee;
      myMappedParameters = mappedParameters;
      myUnmappedParameters = unmappedParameters;
      myUnmappedArguments = unmappedArguments;
      myParametersMappedToVariadicPositionalArguments = parametersMappedToVariadicPositionalArguments;
      myParametersMappedToVariadicKeywordArguments = parametersMappedToVariadicKeywordArguments;
      myMappedTupleParameters = tupleMappedParameters;
    }

    @NotNull
    public PyCallExpression getCallExpression() {
      return myCallExpression;
    }

    @Nullable
    public PyMarkedCallee getMarkedCallee() {
      return myCallee;
    }

    @NotNull
    public Map<PyExpression, PyNamedParameter> getMappedParameters() {
      return myMappedParameters;
    }

    @NotNull
    public List<PyParameter> getUnmappedParameters() {
      return myUnmappedParameters;
    }

    @NotNull
    public List<PyExpression> getUnmappedArguments() {
      return myUnmappedArguments;
    }

    @NotNull
    public List<PyNamedParameter> getParametersMappedToVariadicPositionalArguments() {
      return myParametersMappedToVariadicPositionalArguments;
    }

    @NotNull
    public List<PyNamedParameter> getParametersMappedToVariadicKeywordArguments() {
      return myParametersMappedToVariadicKeywordArguments;
    }

    @NotNull
    public Map<PyExpression, PyTupleParameter> getMappedTupleParameters() {
      return myMappedTupleParameters;
    }
  }

  /**
   * Couples function with a flag describing the way it is called.
   */
  class PyMarkedCallee {
    @NotNull final PyCallable myCallable;
    PyFunction.Modifier myModifier;
    int myImplicitOffset;
    boolean myImplicitlyResolved;

    /**
     * Method-oriented constructor.
     *
     * @param function           the method (or any other callable, but why bother then).
     * @param modifier           classmethod or staticmethod modifier
     * @param offset             implicit argument offset; parameters up to this are implicitly filled in the call.
     * @param implicitlyResolved value for {@link #isImplicitlyResolved()}
     */
    public PyMarkedCallee(@NotNull PyCallable function, PyFunction.Modifier modifier, int offset, boolean implicitlyResolved) {
      myCallable = function;
      myModifier = modifier;
      myImplicitOffset = offset;
      myImplicitlyResolved = implicitlyResolved;
    }

    @NotNull
    public PyCallable getCallable() {
      return myCallable;
    }

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
