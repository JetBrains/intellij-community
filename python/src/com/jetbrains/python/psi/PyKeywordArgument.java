package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;
import com.intellij.lang.ASTNode;

/**
 * @author yole
 */
public interface PyKeywordArgument extends PyExpression {
  @Nullable
  String getKeyword();

  @Nullable
  PyExpression getValueExpression();

  @Nullable
  ASTNode getKeywordNode();
}
