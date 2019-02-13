// This is a generated file. Not intended for manual editing.
package de.thomasrosenau.diffplugin.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class DiffVisitor extends PsiElementVisitor {

  public void visitChanged(@NotNull DiffChanged o) {
    visitPsiElement(o);
  }

  public void visitInfo(@NotNull DiffInfo o) {
    visitPsiElement(o);
  }

  public void visitPlain(@NotNull DiffPlain o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
