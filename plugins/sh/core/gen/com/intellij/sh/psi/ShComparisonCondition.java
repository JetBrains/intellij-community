// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShComparisonCondition extends ShCondition {

  @NotNull
  List<ShCondition> getConditionList();

  @Nullable
  PsiElement getGt();

  @Nullable
  PsiElement getLt();

}
