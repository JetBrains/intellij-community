// This is a generated file. Not intended for manual editing.
package com.jetbrains.commandInterface.gnuCommandLine.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CommandLineOption extends PsiElement {

  @Nullable
  PsiElement getLongOptionNameToken();

  @Nullable
  PsiElement getShortOptionNameToken();

  @Nullable
  @NonNls
  String getOptionName();

  boolean isLong();

}
