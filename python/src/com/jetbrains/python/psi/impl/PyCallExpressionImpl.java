package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
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
  public Callable resolveCalleeFunction(PyResolveContext resolveContext) {
    return PyCallExpressionHelper.resolveCalleeFunction(this, resolveContext);
  }

  public PyMarkedCallee resolveCallee(PyResolveContext resolveContext, int offset) {
    return PyCallExpressionHelper.resolveCallee(this, resolveContext, offset);
  }

  public boolean isCalleeText(@NotNull String... nameCandidates) {
    return PyCallExpressionHelper.isCalleeText(this, nameCandidates);
  }

  @Override
  public String toString() {
    return "PyCallExpression: " + PyUtil.getReadableRepr(getCallee(), true); //or: getCalledFunctionReference().getReferencedName();
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyCallExpressionHelper.getCallType(this, context);
  }
}
