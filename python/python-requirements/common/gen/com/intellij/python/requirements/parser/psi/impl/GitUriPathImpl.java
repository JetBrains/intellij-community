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

public class GitUriPathImpl extends ASTWrapperPsiElement implements GitUriPath {

  public GitUriPathImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitGitUriPath(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<GitUriPathSegment> getGitUriPathSegmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, GitUriPathSegment.class);
  }

  @Override
  @Nullable
  public VcsRevision getVcsRevision() {
    return findChildByClass(VcsRevision.class);
  }

}
