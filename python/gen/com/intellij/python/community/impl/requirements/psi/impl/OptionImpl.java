// This is a generated file. Not intended for manual editing.
package com.intellij.python.community.impl.requirements.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.python.community.impl.requirements.psi.RequirementsTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.python.community.impl.requirements.psi.*;

public class OptionImpl extends ASTWrapperPsiElement implements Option {

  public OptionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitOption(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ConstraintReq getConstraintReq() {
    return findChildByClass(ConstraintReq.class);
  }

  @Override
  @Nullable
  public EditableReq getEditableReq() {
    return findChildByClass(EditableReq.class);
  }

  @Override
  @Nullable
  public ExtraIndexUrlReq getExtraIndexUrlReq() {
    return findChildByClass(ExtraIndexUrlReq.class);
  }

  @Override
  @Nullable
  public FindLinksReq getFindLinksReq() {
    return findChildByClass(FindLinksReq.class);
  }

  @Override
  @Nullable
  public IndexUrlReq getIndexUrlReq() {
    return findChildByClass(IndexUrlReq.class);
  }

  @Override
  @Nullable
  public NoBinaryReq getNoBinaryReq() {
    return findChildByClass(NoBinaryReq.class);
  }

  @Override
  @Nullable
  public NoIndexReq getNoIndexReq() {
    return findChildByClass(NoIndexReq.class);
  }

  @Override
  @Nullable
  public OnlyBinaryReq getOnlyBinaryReq() {
    return findChildByClass(OnlyBinaryReq.class);
  }

  @Override
  @Nullable
  public PreReq getPreReq() {
    return findChildByClass(PreReq.class);
  }

  @Override
  @Nullable
  public PreferBinaryReq getPreferBinaryReq() {
    return findChildByClass(PreferBinaryReq.class);
  }

  @Override
  @Nullable
  public ReferReq getReferReq() {
    return findChildByClass(ReferReq.class);
  }

  @Override
  @Nullable
  public RequireHashesReq getRequireHashesReq() {
    return findChildByClass(RequireHashesReq.class);
  }

  @Override
  @Nullable
  public TrustedHostReq getTrustedHostReq() {
    return findChildByClass(TrustedHostReq.class);
  }

  @Override
  @Nullable
  public UseFeatureReq getUseFeatureReq() {
    return findChildByClass(UseFeatureReq.class);
  }

}
