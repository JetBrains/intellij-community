// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BashCaseCommand extends BashCompositeElement {

  @Nullable
  BashCaseClause getCaseClause();

  @Nullable
  BashCaseClauseSequence getCaseClauseSequence();

  @NotNull
  PsiElement getCase();

  @NotNull
  PsiElement getEsac();

  @NotNull
  PsiElement getIn();

  @NotNull
  PsiElement getWord();

}
