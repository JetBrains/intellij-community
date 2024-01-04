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

public class PathRootlessImpl extends ASTWrapperPsiElement implements PathRootless {

  public PathRootlessImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitPathRootless(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<Segment> getSegmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, Segment.class);
  }

  @Override
  @NotNull
  public SegmentNz getSegmentNz() {
    return findNotNullChildByClass(SegmentNz.class);
  }

}
