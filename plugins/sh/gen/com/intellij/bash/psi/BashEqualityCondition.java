// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashEqualityCondition extends BashCondition {

  @NotNull
  List<BashCondition> getConditionList();

  @Nullable
  PsiElement getEq();

  @Nullable
  PsiElement getNe();

  @Nullable
  PsiElement getRegexp();

}
