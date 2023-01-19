// This is a generated file. Not intended for manual editing.
package com.jetbrains.performancePlugin.lang.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;

public class IJPerfVisitor extends PsiElementVisitor {

  public void visitCommandLine(@NotNull IJPerfCommandLine o) {
    visitPsiElement(o);
  }

  public void visitCommandName(@NotNull IJPerfCommandName o) {
    visitPsiNameIdentifierOwner(o);
  }

  public void visitDelayTypingOption(@NotNull IJPerfDelayTypingOption o) {
    visitPsiElement(o);
  }

  public void visitGotoOption(@NotNull IJPerfGotoOption o) {
    visitPsiElement(o);
  }

  public void visitOption(@NotNull IJPerfOption o) {
    visitPsiElement(o);
  }

  public void visitOptionList(@NotNull IJPerfOptionList o) {
    visitPsiElement(o);
  }

  public void visitSimpleOption(@NotNull IJPerfSimpleOption o) {
    visitPsiElement(o);
  }

  public void visitStatement(@NotNull IJPerfStatement o) {
    visitPsiElement(o);
  }

  public void visitPsiNameIdentifierOwner(@NotNull PsiNameIdentifierOwner o) {
    visitElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
