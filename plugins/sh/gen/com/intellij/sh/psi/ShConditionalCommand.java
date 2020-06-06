// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShConditionalCommand extends ShCommand {

  @Nullable
  ShCondition getCondition();

  @Nullable
  ShRedirection getRedirection();

  @Nullable
  PsiElement getLeftDoubleBracket();

  @Nullable
  PsiElement getLeftSquare();

  @Nullable
  PsiElement getRightDoubleBracket();

  @Nullable
  PsiElement getRightSquare();

}
