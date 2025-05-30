package com.intellij.sh.frontend.codeInsight;

import com.intellij.psi.PsiReference;
import com.intellij.sh.codeInsight.ShPsiReferenceSupport;
import com.intellij.sh.psi.ShLiteral;
import com.intellij.sh.psi.ShLiteralExpression;
import com.intellij.sh.psi.ShVariable;
import org.jetbrains.annotations.NotNull;

public class ShFrontendPsiReferenceSupport implements ShPsiReferenceSupport {
  @Override
  public PsiReference @NotNull [] getReferences(@NotNull ShLiteral o) {
    return PsiReference.EMPTY_ARRAY;
  }

  @Override
  public PsiReference @NotNull [] getReferences(@NotNull ShLiteralExpression o) {
    return PsiReference.EMPTY_ARRAY;
  }

  @Override
  public PsiReference @NotNull [] getReferences(@NotNull ShVariable o) {
    return PsiReference.EMPTY_ARRAY;
  }
}