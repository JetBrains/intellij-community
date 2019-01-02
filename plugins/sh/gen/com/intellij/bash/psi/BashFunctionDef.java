// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashFunctionDef extends BashCompositeElement {

  @Nullable
  BashGroupCommand getGroupCommand();

  @Nullable
  PsiElement getLeftParen();

  @Nullable
  PsiElement getRightParen();

  @Nullable
  PsiElement getFunction();

  @Nullable
  PsiElement getWord();

}
