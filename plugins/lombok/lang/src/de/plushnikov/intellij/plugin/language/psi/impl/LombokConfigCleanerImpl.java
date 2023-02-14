// This is a generated file. Not intended for manual editing.
package de.plushnikov.intellij.plugin.language.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static de.plushnikov.intellij.plugin.language.psi.LombokConfigTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import de.plushnikov.intellij.plugin.language.psi.*;

public class LombokConfigCleanerImpl extends ASTWrapperPsiElement implements LombokConfigCleaner {

  public LombokConfigCleanerImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LombokConfigVisitor visitor) {
    visitor.visitCleaner(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LombokConfigVisitor) accept((LombokConfigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  public String getKey() {
    return LombokConfigPsiUtil.getKey(this);
  }

}
