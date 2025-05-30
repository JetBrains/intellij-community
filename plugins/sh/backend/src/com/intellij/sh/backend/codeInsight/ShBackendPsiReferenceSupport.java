package com.intellij.sh.backend.codeInsight;

import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.sh.codeInsight.ShPsiReferenceSupport;
import com.intellij.sh.psi.ShLiteral;
import com.intellij.sh.psi.ShLiteralExpression;
import com.intellij.sh.psi.ShString;
import com.intellij.sh.psi.ShVariable;
import org.jetbrains.annotations.NotNull;

public class ShBackendPsiReferenceSupport implements ShPsiReferenceSupport {
  @Override
  public PsiReference @NotNull [] getReferences(@NotNull ShLiteral o) {
    if (o instanceof ShString || o.getWord() != null) {
      PsiReference[] array = ReferenceProvidersRegistry.getReferencesFromProviders(o);
      int length = array.length;
      PsiReference[] result = new PsiReference[length + 2];
      System.arraycopy(array, 0, result, 2, length);
      result[0] = new ShIncludeCommandReference(o);
      result[1] = new ShFunctionReference(o);
      return result;
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @Override
  public PsiReference @NotNull [] getReferences(@NotNull ShLiteralExpression o) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(o);
  }

  @Override
  public PsiReference @NotNull [] getReferences(@NotNull ShVariable o) {
    return PsiReference.EMPTY_ARRAY;
  }
}