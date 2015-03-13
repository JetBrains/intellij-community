// This is a generated file. Not intended for manual editing.
package com.jetbrains.commandInterface.gnuCommandLine.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class CommandLineVisitor extends PsiElementVisitor {

  public void visitArgument(@NotNull CommandLineArgument o) {
    visitPsiElement(o);
  }

  public void visitCommand(@NotNull CommandLineCommand o) {
    visitPsiElement(o);
  }

  public void visitOption(@NotNull CommandLineOption o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
