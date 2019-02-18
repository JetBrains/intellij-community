// This is a generated file. Not intended for manual editing.
package de.thomasrosenau.diffplugin.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class DiffVisitor extends PsiElementVisitor {

  public void visitAnyLine(@NotNull DiffAnyLine o) {
    visitPsiElement(o);
  }

  public void visitConsoleCommand(@NotNull DiffConsoleCommand o) {
    visitPsiElement(o);
  }

  public void visitContextDiff(@NotNull DiffContextDiff o) {
    visitPsiElement(o);
  }

  public void visitContextFromFileLine(@NotNull DiffContextFromFileLine o) {
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

  public void visitContextToFileLine(@NotNull DiffContextToFileLine o) {
    visitPsiElement(o);
  }

  public void visitLeadingText(@NotNull DiffLeadingText o) {
    visitPsiElement(o);
  }

  public void visitNormalDiff(@NotNull DiffNormalDiff o) {
    visitPsiElement(o);
  }

  public void visitNormalHunk(@NotNull DiffNormalHunk o) {
    visitPsiElement(o);
  }

  public void visitNormalHunkAdd(@NotNull DiffNormalHunkAdd o) {
    visitPsiElement(o);
  }

  public void visitNormalHunkChange(@NotNull DiffNormalHunkChange o) {
    visitPsiElement(o);
  }

  public void visitNormalHunkDelete(@NotNull DiffNormalHunkDelete o) {
    visitPsiElement(o);
  }

  public void visitTrailingText(@NotNull DiffTrailingText o) {
    visitPsiElement(o);
  }

  public void visitUnifiedDiff(@NotNull DiffUnifiedDiff o) {
    visitPsiElement(o);
  }

  public void visitUnifiedHunk(@NotNull DiffUnifiedHunk o) {
    visitPsiElement(o);
  }

  public void visitUnifiedLine(@NotNull DiffUnifiedLine o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
