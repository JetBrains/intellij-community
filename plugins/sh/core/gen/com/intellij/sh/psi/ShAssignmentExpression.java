// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;

public interface ShAssignmentExpression extends ShBinaryExpression, PsiNameIdentifierOwner {

  @Nullable
  PsiElement getAssign();

  @Nullable
  PsiElement getBitAndAssign();

  @Nullable
  PsiElement getBitOrAssign();

  @Nullable
  PsiElement getBitXorAssign();

  @Nullable
  PsiElement getDivAssign();

  @Nullable
  PsiElement getMinusAssign();

  @Nullable
  PsiElement getModAssign();

  @Nullable
  PsiElement getMultAssign();

  @Nullable
  PsiElement getPlusAssign();

  @Nullable
  PsiElement getShiftLeftAssign();

  @Nullable
  PsiElement getShiftRightAssign();

}
