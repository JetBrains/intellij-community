package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;

/**
 * @author yole
 */
public class PyAnnotationImpl extends PyElementImpl implements PyAnnotation {
  public PyAnnotationImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public PyExpression getValue() {
    return findChildByClass(PyExpression.class);
  }

  @Override
  public PyClass resolveToClass() {
    PyExpression expr = getValue();
    if (expr instanceof PyReferenceExpression) {
      final PsiPolyVariantReference reference = ((PyReferenceExpression)expr).getReference();
      final PsiElement result = reference.resolve();
      if (result instanceof PyClass) {
        return (PyClass) result;
      }
    }
    return null;
  }
}
