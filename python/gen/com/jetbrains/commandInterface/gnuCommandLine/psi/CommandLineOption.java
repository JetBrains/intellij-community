// This is a generated file. Not intended for manual editing.
package com.jetbrains.commandInterface.gnuCommandLine.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.jetbrains.commandInterface.gnuCommandLine.CommandLinePart;
import com.jetbrains.commandInterface.command.Option;

public interface CommandLineOption extends CommandLinePart {

  @Nullable
  PsiElement getLongOptionNameToken();

  @Nullable
  PsiElement getShortOptionNameToken();

  @Nullable
  @NonNls
  String getOptionName();

  boolean isLong();

  @Nullable
  Option findRealOption();

}
