package com.intellij.sh.backend.codeInsight

import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.sh.codeInsight.ShPsiReferenceSupport
import com.intellij.sh.psi.*
import org.jetbrains.annotations.NotNull

class ShBackendPsiReferenceSupport : ShPsiReferenceSupport {
  override fun getReferences(@NotNull o: ShLiteral): Array<PsiReference> {
    if (o is ShString || o.word != null) {
      val array = ReferenceProvidersRegistry.getReferencesFromProviders(o)
      val length = array.size
      val result = arrayOfNulls<PsiReference>(length + 2)
      System.arraycopy(array, 0, result, 2, length)
      result[0] = ShIncludeCommandReference(o)
      result[1] = ShFunctionReference(o)
      val references = arrayOf(
        ShIncludeCommandReference(o),
        ShFunctionReference(o),
        *array
      )
      return references
    }
    return PsiReference.EMPTY_ARRAY
  }

  override fun getReferences(@NotNull o: ShLiteralExpression): Array<PsiReference> {
    return ReferenceProvidersRegistry.getReferencesFromProviders(o)
  }

  override fun getReferences(@NotNull o: ShVariable): Array<PsiReference> {
    return PsiReference.EMPTY_ARRAY
  }

  override fun getReferences(@NotNull o: ShLiteralOperation): Array<PsiReference> {
    return ReferenceProvidersRegistry.getReferencesFromProviders(o)
  }
}