// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashFunctionDef extends BashCompositeElement {

  @NotNull
  BashGroupCommand getGroupCommand();

  @Nullable
  BashString getString();

  @Nullable
  PsiElement getLeftParen();

  @Nullable
  PsiElement getRightParen();

  @Nullable
  PsiElement getFunction();

  @Nullable
  PsiElement getInt();

  @Nullable
  PsiElement getNumber();

  @Nullable
  PsiElement getVariable();

  @Nullable
  PsiElement getWord();

}
