package com.intellij.sh.codeInsight

import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiReference
import com.intellij.sh.psi.ShLiteral
import com.intellij.sh.psi.ShLiteralExpression
import com.intellij.sh.psi.ShLiteralOperation
import com.intellij.sh.psi.ShVariable
import org.jetbrains.annotations.NotNull

interface ShPsiReferenceSupport {
  fun getReferences(@NotNull o: ShLiteral): Array<PsiReference>

  fun getReferences(@NotNull o: ShLiteralExpression): Array<PsiReference>

  fun getReferences(@NotNull o: ShVariable): Array<PsiReference>

  fun getReferences(@NotNull o: ShLiteralOperation): Array<PsiReference>

  companion object {
    @JvmStatic
    fun getInstance(): ShPsiReferenceSupport = serviceOrNull<ShPsiReferenceSupport>() ?: DumbShPsiReferenceSupport
  }
}