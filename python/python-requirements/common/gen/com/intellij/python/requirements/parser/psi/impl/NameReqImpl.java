// This is a generated file. Not intended for manual editing.
package com.intellij.python.requirements.parser.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.python.requirements.parser.psi.RequirementsTypes.*;
import com.intellij.python.requirements.parser.psi.*;

public class NameReqImpl extends RequirementsReferenceHost implements NameReq {

  public NameReqImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitNameReq(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public EditableOption getEditableOption() {
    return findChildByClass(EditableOption.class);
  }

  @Override
  @Nullable
  public Extras getExtras() {
    return findChildByClass(Extras.class);
  }

  @Override
  @NotNull
  public List<LongOption> getLongOptionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, LongOption.class);
  }

  @Override
  @NotNull
  public PackageName getPackageName() {
    return findNotNullChildByClass(PackageName.class);
  }

  @Override
  @Nullable
  public QuotedMarker getQuotedMarker() {
    return findChildByClass(QuotedMarker.class);
  }

  @Override
  @Nullable
  public Versionspec getVersionspec() {
    return findChildByClass(Versionspec.class);
  }

}
