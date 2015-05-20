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
import com.jetbrains.commandInterface.command.Option;

public class CommandLineOptionImpl extends CommandLineElement implements CommandLineOption {

  public CommandLineOptionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CommandLineVisitor) ((CommandLineVisitor)visitor).visitOption(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getLongOptionNameToken() {
    return findChildByType(LONG_OPTION_NAME_TOKEN);
  }

  @Override
  @Nullable
  public PsiElement getShortOptionNameToken() {
    return findChildByType(SHORT_OPTION_NAME_TOKEN);
  }

  @Nullable
  @NonNls
  public String getOptionName() {
    return CommandLinePsiImplUtils.getOptionName(this);
  }

  public boolean isLong() {
    return CommandLinePsiImplUtils.isLong(this);
  }

  @Nullable
  public Option findRealOption() {
    return CommandLinePsiImplUtils.findRealOption(this);
  }

}
