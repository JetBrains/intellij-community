// This is a generated file. Not intended for manual editing.
package de.plushnikov.intellij.plugin.language.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class LombokConfigVisitor extends PsiElementVisitor {

  public void visitCleaner(@NotNull LombokConfigCleaner o) {
    visitPsiElement(o);
  }

  public void visitOperation(@NotNull LombokConfigOperation o) {
    visitPsiElement(o);
  }

  public void visitProperty(@NotNull LombokConfigProperty o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
