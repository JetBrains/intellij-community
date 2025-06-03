package com.intellij.sh.codeInsight

import com.intellij.psi.PsiReference
import com.intellij.sh.psi.*
import org.jetbrains.annotations.NotNull

interface ShPsiReferenceSupport {
  fun getReferences(@NotNull o: ShLiteral): Array<PsiReference>

  fun getReferences(@NotNull o: ShLiteralExpression): Array<PsiReference>

  fun getReferences(@NotNull o: ShVariable): Array<PsiReference>

  fun getReferences(@NotNull o: ShLiteralOperation): Array<PsiReference>
}