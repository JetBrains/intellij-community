// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ShAssignmentCommand extends ShCommand {

  @Nullable
  ShArrayExpression getArrayExpression();

  @Nullable
  ShAssignmentList getAssignmentList();

  @NotNull
  ShLiteral getLiteral();

  @Nullable
  PsiElement getPlusAssign();

}
