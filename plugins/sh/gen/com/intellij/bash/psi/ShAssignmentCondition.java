// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShAssignmentCondition extends ShCondition {

  @NotNull
  List<ShCondition> getConditionList();

  @NotNull
  PsiElement getAssign();

}
