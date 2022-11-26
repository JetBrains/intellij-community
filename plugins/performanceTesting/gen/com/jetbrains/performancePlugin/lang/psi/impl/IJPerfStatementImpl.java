// This is a generated file. Not intended for manual editing.
package com.jetbrains.performancePlugin.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.performancePlugin.lang.psi.IJPerfElementTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.jetbrains.performancePlugin.lang.psi.*;

public class IJPerfStatementImpl extends ASTWrapperPsiElement implements IJPerfStatement {

  public IJPerfStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull IJPerfVisitor visitor) {
    visitor.visitStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof IJPerfVisitor) accept((IJPerfVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public IJPerfCommandLine getCommandLine() {
    return findChildByClass(IJPerfCommandLine.class);
  }

}
