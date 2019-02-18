// This is a generated file. Not intended for manual editing.
package de.thomasrosenau.diffplugin.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static de.thomasrosenau.diffplugin.psi.DiffTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import de.thomasrosenau.diffplugin.psi.*;

public class DiffLeadingTextImpl extends ASTWrapperPsiElement implements DiffLeadingText {

  public DiffLeadingTextImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DiffVisitor visitor) {
    visitor.visitLeadingText(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DiffVisitor) accept((DiffVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<DiffAnyLine> getAnyLineList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, DiffAnyLine.class);
  }

  @Override
  @Nullable
  public DiffConsoleCommand getConsoleCommand() {
    return findChildByClass(DiffConsoleCommand.class);
  }

}
