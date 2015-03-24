// This is a generated file. Not intended for manual editing.
package com.jetbrains.commandInterface.commandLine.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.commandInterface.commandLine.CommandLineElementTypes.*;
import com.jetbrains.commandInterface.commandLine.CommandLineElement;
import com.jetbrains.commandInterface.commandLine.psi.*;

public class CommandLineCommandImpl extends CommandLineElement implements CommandLineCommand {

  public CommandLineCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CommandLineVisitor) ((CommandLineVisitor)visitor).visitCommand(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public PsiElement getLiteralStartsFromLetter() {
    return findNotNullChildByType(LITERAL_STARTS_FROM_LETTER);
  }

}
