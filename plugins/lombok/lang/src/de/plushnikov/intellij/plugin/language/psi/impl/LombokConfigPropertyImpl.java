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

public class LombokConfigPropertyImpl extends ASTWrapperPsiElement implements LombokConfigProperty {

  public LombokConfigPropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LombokConfigVisitor visitor) {
    visitor.visitProperty(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LombokConfigVisitor) accept((LombokConfigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public LombokConfigOperation getOperation() {
    return findNotNullChildByClass(LombokConfigOperation.class);
  }

  @Override
  public String getKey() {
    return LombokConfigPsiUtil.getKey(this);
  }

  @Override
  public String getValue() {
    return LombokConfigPsiUtil.getValue(this);
  }

  @Override
  public String getSign() {
    return LombokConfigPsiUtil.getSign(this);
  }

}
