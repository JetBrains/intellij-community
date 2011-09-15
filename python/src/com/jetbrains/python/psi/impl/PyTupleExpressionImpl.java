package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;

/**
 * @author yole
 */
public class PyTupleExpressionImpl extends PyElementImpl implements PyTupleExpression {
  public PyTupleExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTupleExpression(this);
  }

  @NotNull
  public PyExpression[] getElements() {
    return childrenToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), PyExpression.EMPTY_ARRAY);
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    final PyExpression[] elements = getElements();
    final PyType[] types = new PyType[elements.length];
    for (int i = 0; i < types.length; i++) {
      types [i] = context.getType(elements [i]);
    }
    return new PyTupleType(this, types);
  }

  public Iterator<PyExpression> iterator() {
    return Arrays.<PyExpression>asList(getElements()).iterator();
  }
}
