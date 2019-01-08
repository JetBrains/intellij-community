// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashAssignmentCommand extends BashCommand {

  @Nullable
  BashAssignmentList getAssignmentList();

  @Nullable
  BashVariable getVariable();

  @NotNull
  PsiElement getEq();

  @Nullable
  PsiElement getAssignmentWord();

  @Nullable
  PsiElement getWord();

}
