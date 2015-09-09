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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.nameResolver.FQNamesProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyCallExpressionImpl extends PyElementImpl implements PyCallExpression {

  public PyCallExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyCallExpression(this);
  }

  @Nullable
  public PyExpression getCallee() {
    // peel off any parens, because we may have smth like (lambda x: x+1)(2)
    PsiElement seeker = getFirstChild();
    while (seeker instanceof PyParenthesizedExpression) seeker = ((PyParenthesizedExpression)seeker).getContainedExpression();
    return seeker instanceof PyExpression ? (PyExpression) seeker : null;
  }

  public PyArgumentList getArgumentList() {
    return PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
  }

  @NotNull
  public PyExpression[] getArguments() {
    final PyArgumentList argList = getArgumentList();
    return argList != null ? argList.getArguments() : PyExpression.EMPTY_ARRAY;
  }

  @Override
  public <T extends PsiElement> T getArgument(int index, Class<T> argClass) {
    PyExpression[] args = getArguments();
    return args.length > index && argClass.isInstance(args[index]) ? argClass.cast(args[index]) : null;
  }

  @Override
  public <T extends PsiElement> T getArgument(int index, String keyword, Class<T> argClass) {
    final PyExpression argument = getKeywordArgument(keyword);
    if (argument != null) {
      return argClass.isInstance(argument) ? argClass.cast(argument) : null;
    }
    return getArgument(index, argClass);
  }

  @Nullable
  @Override
  public <T extends PsiElement> T getArgument(@NotNull final FunctionParameter parameter, @NotNull final Class<T> argClass) {
    return PyCallExpressionHelper.getArgument(parameter, argClass, this);
  }

  @Override
  public PyExpression getKeywordArgument(String keyword) {
    return PyCallExpressionHelper.getKeywordArgument(this, keyword);
  }

  public void addArgument(PyExpression expression) {
    PyCallExpressionHelper.addArgument(this, expression);
  }

  public PyMarkedCallee resolveCallee(PyResolveContext resolveContext) {
    return PyCallExpressionHelper.resolveCallee(this, resolveContext);
  }

  @Override
  public PyCallable resolveCalleeFunction(PyResolveContext resolveContext) {
    return PyCallExpressionHelper.resolveCalleeFunction(this, resolveContext);
  }

  public PyMarkedCallee resolveCallee(PyResolveContext resolveContext, int offset) {
    return PyCallExpressionHelper.resolveCallee(this, resolveContext, offset);
  }

  @NotNull
  @Override
  public PyArgumentsMapping mapArguments(@NotNull PyResolveContext resolveContext) {
    return PyCallExpressionHelper.mapArguments(this, resolveContext, 0);
  }

  @NotNull
  @Override
  public PyArgumentsMapping mapArguments(@NotNull PyResolveContext resolveContext, int implicitOffset) {
    return PyCallExpressionHelper.mapArguments(this, resolveContext, implicitOffset);
  }

  @Override
  public boolean isCalleeText(@NotNull String... nameCandidates) {
    return PyCallExpressionHelper.isCalleeText(this, nameCandidates);
  }

  @Override
  public boolean isCallee(@NotNull final FQNamesProvider... name) {
    return PyCallExpressionHelper.isCallee(this, name);
  }

  @Override
  public String toString() {
    return "PyCallExpression: " + PyUtil.getReadableRepr(getCallee(), true); //or: getCalledFunctionReference().getReferencedName();
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyCallExpressionHelper.getCallType(this, context);
  }
}
