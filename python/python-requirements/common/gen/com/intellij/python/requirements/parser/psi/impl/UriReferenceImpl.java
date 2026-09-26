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

public class UriReferenceImpl extends ASTWrapperPsiElement implements UriReference {

  public UriReferenceImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitUriReference(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public BzrLaunchpadUri getBzrLaunchpadUri() {
    return findChildByClass(BzrLaunchpadUri.class);
  }

  @Override
  @Nullable
  public EditableOption getEditableOption() {
    return findChildByClass(EditableOption.class);
  }

  @Override
  @Nullable
  public GitUri getGitUri() {
    return findChildByClass(GitUri.class);
  }

  @Override
  @Nullable
  public QuotedMarker getQuotedMarker() {
    return findChildByClass(QuotedMarker.class);
  }

  @Override
  @Nullable
  public Uri getUri() {
    return findChildByClass(Uri.class);
  }

}
