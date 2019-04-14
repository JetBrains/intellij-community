// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashAssignmentCommand extends BashCommand {

  @Nullable
  BashArrayExpression getArrayExpression();

  @Nullable
  BashAssignmentList getAssignmentList();

  @NotNull
  BashLiteral getLiteral();

  @Nullable
  PsiElement getPlusAssign();

}
