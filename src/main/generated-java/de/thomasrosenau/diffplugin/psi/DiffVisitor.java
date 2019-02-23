// This is a generated file. Not intended for manual editing.
package de.thomasrosenau.diffplugin.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class DiffVisitor extends PsiElementVisitor {

  public void visitConsoleCommand(@NotNull DiffConsoleCommand o) {
    visitPsiElement(o);
  }

  public void visitContextHunk(@NotNull DiffContextHunk o) {
    visitPsiElement(o);
  }

  public void visitContextHunkFrom(@NotNull DiffContextHunkFrom o) {
    visitPsiElement(o);
  }

  public void visitContextHunkTo(@NotNull DiffContextHunkTo o) {
    visitPsiElement(o);
  }

  public void visitGitBinaryPatch(@NotNull DiffGitBinaryPatch o) {
    visitPsiElement(o);
  }

  public void visitGitFooter(@NotNull DiffGitFooter o) {
    visitPsiElement(o);
  }

  public void visitGitHeader(@NotNull DiffGitHeader o) {
    visitPsiElement(o);
  }

  public void visitNormalHunk(@NotNull DiffNormalHunk o) {
    visitPsiElement(o);
  }

  public void visitUnifiedHunk(@NotNull DiffUnifiedHunk o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
