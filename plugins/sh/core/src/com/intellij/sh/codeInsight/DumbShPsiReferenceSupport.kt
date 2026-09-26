package com.intellij.sh.codeInsight

import com.intellij.psi.PsiReference
import com.intellij.sh.psi.ShLiteral
import com.intellij.sh.psi.ShLiteralExpression
import com.intellij.sh.psi.ShLiteralOperation
import com.intellij.sh.psi.ShVariable
import org.jetbrains.annotations.NotNull

internal object DumbShPsiReferenceSupport : ShPsiReferenceSupport {
  override fun getReferences(@NotNull o: ShLiteral): Array<PsiReference> {
    return PsiReference.EMPTY_ARRAY
  }

  override fun getReferences(@NotNull o: ShLiteralExpression): Array<PsiReference> {
    return PsiReference.EMPTY_ARRAY
  }

  override fun getReferences(@NotNull o: ShVariable): Array<PsiReference> {
    return PsiReference.EMPTY_ARRAY
  }

  override fun getReferences(@NotNull o: ShLiteralOperation): Array<PsiReference> {
    return PsiReference.EMPTY_ARRAY
  }
}