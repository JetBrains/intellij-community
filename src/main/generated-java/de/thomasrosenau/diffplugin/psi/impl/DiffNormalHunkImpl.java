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

public class DiffNormalHunkImpl extends ASTWrapperPsiElement implements DiffNormalHunk {

  public DiffNormalHunkImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DiffVisitor visitor) {
    visitor.visitNormalHunk(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DiffVisitor) accept((DiffVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public DiffNormalHunkAdd getNormalHunkAdd() {
    return findChildByClass(DiffNormalHunkAdd.class);
  }

  @Override
  @Nullable
  public DiffNormalHunkChange getNormalHunkChange() {
    return findChildByClass(DiffNormalHunkChange.class);
  }

  @Override
  @Nullable
  public DiffNormalHunkDelete getNormalHunkDelete() {
    return findChildByClass(DiffNormalHunkDelete.class);
  }

}
