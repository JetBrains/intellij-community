// This is a generated file. Not intended for manual editing.
package com.jetbrains.commandInterface.commandLine.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import com.jetbrains.commandInterface.commandLine.CommandLinePart;

public class CommandLineVisitor extends PsiElementVisitor {

  public void visitArgument(@NotNull CommandLineArgument o) {
    visitPart(o);
  }

  public void visitCommand(@NotNull CommandLineCommand o) {
    visitPart(o);
  }

  public void visitOption(@NotNull CommandLineOption o) {
    visitPart(o);
  }

  public void visitPart(@NotNull CommandLinePart o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
