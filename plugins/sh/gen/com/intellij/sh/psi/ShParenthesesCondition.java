// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShParenthesesCondition extends ShCondition {

  @Nullable
  ShCondition getCondition();

  @NotNull
  PsiElement getLeftParen();

  @Nullable
  PsiElement getRightParen();

}
