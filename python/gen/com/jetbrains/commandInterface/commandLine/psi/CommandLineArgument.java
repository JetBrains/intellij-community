// This is a generated file. Not intended for manual editing.
package com.jetbrains.commandInterface.commandLine.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.jetbrains.commandInterface.commandLine.CommandLinePart;
import com.jetbrains.commandInterface.command.Argument;
import com.jetbrains.commandInterface.command.Help;
import com.jetbrains.commandInterface.command.Option;

public interface CommandLineArgument extends CommandLinePart {

  @Nullable
  PsiElement getLiteralStartsFromDigit();

  @Nullable
  PsiElement getLiteralStartsFromLetter();

  @Nullable
  PsiElement getLiteralStartsFromSymbol();

  @Nullable
  PsiElement getSingleQSpacedLiteralStartsFromDigit();

  @Nullable
  PsiElement getSingleQSpacedLiteralStartsFromLetter();

  @Nullable
  PsiElement getSingleQSpacedLiteralStartsFromSymbol();

  @Nullable
  PsiElement getSpacedLiteralStartsFromDigit();

  @Nullable
  PsiElement getSpacedLiteralStartsFromLetter();

  @Nullable
  PsiElement getSpacedLiteralStartsFromSymbol();

  @Nullable
  Option findOptionForOptionArgument();

  @Nullable
  Argument findRealArgument();

  @Nullable
  Help findBestHelp();

  @NotNull
  String getValueNoQuotes();

}
