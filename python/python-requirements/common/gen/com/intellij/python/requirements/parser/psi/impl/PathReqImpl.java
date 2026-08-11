// This is a generated file. Not intended for manual editing.
package com.intellij.python.requirements.parser.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.python.requirements.parser.psi.RequirementsTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.python.requirements.parser.psi.*;

public class PathReqImpl extends ASTWrapperPsiElement implements PathReq {

  public PathReqImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitPathReq(this);
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
  public Path getPath() {
    return findNotNullChildByClass(Path.class);
  }

  @Override
  @Nullable
  public QuotedMarker getQuotedMarker() {
    return findChildByClass(QuotedMarker.class);
  }

}
