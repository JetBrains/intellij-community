// This is a generated file. Not intended for manual editing.
package com.jetbrains.commandInterface.gnuCommandLine.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.commandInterface.gnuCommandLine.CommandLineElementTypes.*;
import com.jetbrains.commandInterface.gnuCommandLine.CommandLineElement;
import com.jetbrains.commandInterface.gnuCommandLine.psi.*;
import com.jetbrains.commandInterface.command.Argument;
import com.jetbrains.commandInterface.command.Option;

public class CommandLineArgumentImpl extends CommandLineElement implements CommandLineArgument {

  public CommandLineArgumentImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CommandLineVisitor) ((CommandLineVisitor)visitor).visitArgument(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getLiteralStartsFromDigit() {
    return findChildByType(LITERAL_STARTS_FROM_DIGIT);
  }

  @Override
  @Nullable
  public PsiElement getLiteralStartsFromLetter() {
    return findChildByType(LITERAL_STARTS_FROM_LETTER);
  }

  @Nullable
  public Option findOptionForOptionArgument() {
    return CommandLinePsiImplUtils.findOptionForOptionArgument(this);
  }

  @Nullable
  public Argument findRealArgument() {
    return CommandLinePsiImplUtils.findRealArgument(this);
  }

  @Nullable
  public String findBestHelpText() {
    return CommandLinePsiImplUtils.findBestHelpText(this);
  }

}
