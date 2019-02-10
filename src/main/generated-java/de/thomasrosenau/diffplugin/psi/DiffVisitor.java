// This is a generated file. Not intended for manual editing.
package de.thomasrosenau.diffplugin.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class DiffVisitor extends PsiElementVisitor {

  public void visitLine(@NotNull DiffLine o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
