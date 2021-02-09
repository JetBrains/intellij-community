// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.sh.ShTypes.*;
import com.intellij.sh.psi.*;

public class ShFunctionDefinitionImpl extends ShFunctionDefinitionMixin implements ShFunctionDefinition {

  public ShFunctionDefinitionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitFunctionDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ShBlock getBlock() {
    return findChildByClass(ShBlock.class);
  }

  @Override
  @Nullable
  public ShParenthesesBlock getParenthesesBlock() {
    return findChildByClass(ShParenthesesBlock.class);
  }

  @Override
  @Nullable
  public PsiElement getLeftParen() {
    return findChildByType(LEFT_PAREN);
  }

  @Override
  @Nullable
  public PsiElement getRightParen() {
    return findChildByType(RIGHT_PAREN);
  }

  @Override
  @Nullable
  public PsiElement getFunction() {
    return findChildByType(FUNCTION);
  }

  @Override
  @Nullable
  public PsiElement getWord() {
    return findChildByType(WORD);
  }

}
