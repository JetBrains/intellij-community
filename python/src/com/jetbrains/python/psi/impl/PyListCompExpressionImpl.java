package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.psi.ComprhForComponent;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyListCompExpressionImpl extends PyComprehensionElementImpl implements PyListCompExpression {
  public PyListCompExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyListCompExpression(this);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    for (ComprhForComponent component : getForComponents()) {
      if (component != null) {
        //TODO: this needs to restrict resolution based on nesting
        // for example, this is not valid (the i in the first for should not resolve):
        //    x  for x in i for i in y
        if (!component.getIteratorVariable().processDeclarations(processor, substitutor, null, place)) return false;
      }
    }
    return true;
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    return null;
  }

}
