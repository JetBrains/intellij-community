package com.intellij.sh.codeInsight;

import com.intellij.psi.PsiReference;
import com.intellij.sh.psi.ShLiteral;
import com.intellij.sh.psi.ShLiteralExpression;
import com.intellij.sh.psi.ShVariable;
import org.jetbrains.annotations.NotNull;

public interface ShPsiReferenceSupport {
  PsiReference @NotNull [] getReferences(@NotNull ShLiteral o);

  PsiReference @NotNull [] getReferences(@NotNull ShLiteralExpression o);

  PsiReference @NotNull [] getReferences(@NotNull ShVariable o);
}