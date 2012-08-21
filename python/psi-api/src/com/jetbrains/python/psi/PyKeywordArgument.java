package com.jetbrains.python.psi;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.Nullable;
import com.intellij.lang.ASTNode;

/**
 * @author yole
 */
public interface PyKeywordArgument extends PyExpression, PsiNamedElement {
  @Nullable
  String getKeyword();

  @Nullable
  PyExpression getValueExpression();

  @Nullable
  ASTNode getKeywordNode();
}
