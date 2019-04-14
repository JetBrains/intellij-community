// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.bash.BashTypes.*;
import com.intellij.bash.psi.*;

public class BashGenericCommandDirectiveImpl extends BashSimpleCommandImpl implements BashGenericCommandDirective {

  public BashGenericCommandDirectiveImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitGenericCommandDirective(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public BashArithmeticExpansion getArithmeticExpansion() {
    return findChildByClass(BashArithmeticExpansion.class);
  }

  @Override
  @Nullable
  public BashBashExpansion getBashExpansion() {
    return findChildByClass(BashBashExpansion.class);
  }

  @Override
  @Nullable
  public BashHeredoc getHeredoc() {
    return findChildByClass(BashHeredoc.class);
  }

  @Override
  @Nullable
  public BashLiteral getLiteral() {
    return findChildByClass(BashLiteral.class);
  }

  @Override
  @Nullable
  public BashOldArithmeticExpansion getOldArithmeticExpansion() {
    return findChildByClass(BashOldArithmeticExpansion.class);
  }

  @Override
  @Nullable
  public BashRedirection getRedirection() {
    return findChildByClass(BashRedirection.class);
  }

  @Override
  @Nullable
  public BashShellParameterExpansion getShellParameterExpansion() {
    return findChildByClass(BashShellParameterExpansion.class);
  }

  @Override
  @Nullable
  public BashVariable getVariable() {
    return findChildByClass(BashVariable.class);
  }

  @Override
  @Nullable
  public PsiElement getBang() {
    return findChildByType(BANG);
  }

  @Override
  @Nullable
  public PsiElement getDollar() {
    return findChildByType(DOLLAR);
  }

  @Override
  @Nullable
  public PsiElement getFiledescriptor() {
    return findChildByType(FILEDESCRIPTOR);
  }

}
