package com.intellij.sh.codeInsight

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiReference
import com.intellij.sh.psi.*
import org.jetbrains.annotations.NotNull

interface ShPsiReferenceSupport {
  fun getReferences(@NotNull o: ShLiteral): Array<PsiReference>

  fun getReferences(@NotNull o: ShLiteralExpression): Array<PsiReference>

  fun getReferences(@NotNull o: ShVariable): Array<PsiReference>

  fun getReferences(@NotNull o: ShLiteralOperation): Array<PsiReference>

  companion object {
    @JvmStatic
    fun getInstance(): ShPsiReferenceSupport = ApplicationManager.getApplication().getService(ShPsiReferenceSupport::class.java)
  }
}