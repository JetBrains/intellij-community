// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashSubshell extends BashCompositeElement {

  @NotNull
  BashCompoundList getCompoundList();

  @NotNull
  PsiElement getLeftParen();

  @NotNull
  PsiElement getRightParen();

}
