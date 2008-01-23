/*
 * @author max
 */
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.SourceUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ExpressionPsiElement extends CompositePsiElement {
  public ExpressionPsiElement(final IElementType type) {
    super(type);
  }

  public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
    boolean needParenth = ReplaceExpressionUtil.isNeedParenthesis(child, newElement);
    if (needParenth) {
      newElement = SourceUtil.addParenthToReplacedChild(JavaElementType.PARENTH_EXPRESSION, newElement, getManager());
    }
    super.replaceChildInternal(child, newElement);
  }
}