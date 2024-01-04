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

public class UserinfoImpl extends ASTWrapperPsiElement implements Userinfo {

  public UserinfoImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitUserinfo(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<EnvVariable> getEnvVariableList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, EnvVariable.class);
  }

  @Override
  @NotNull
  public List<PctEncoded> getPctEncodedList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, PctEncoded.class);
  }

  @Override
  @NotNull
  public List<Unreserved> getUnreservedList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, Unreserved.class);
  }

}
