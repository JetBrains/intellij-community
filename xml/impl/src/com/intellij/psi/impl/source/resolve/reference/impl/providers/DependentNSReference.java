package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DependentNSReference extends BasicAttributeValueReference {
  private final URLReference myReference;
  private final boolean myForceFetchResultValid;

  public DependentNSReference(final PsiElement element, TextRange range, URLReference ref) {
    this(element, range, ref, false);
  }

  public DependentNSReference(final PsiElement element,
                              TextRange range,
                              URLReference ref,
                              boolean valid) {
    super(element, range);
    myReference = ref;
    myForceFetchResultValid = valid;
  }

  @Nullable
  public PsiElement resolve() {
    final String canonicalText = getCanonicalText();
    final PsiFile file = ExternalResourceManager.getInstance().getResourceLocation(canonicalText, myElement.getContainingFile(), null);
    if (file != null) return file;
    return myReference.resolve();
  }

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }

  public boolean isForceFetchResultValid() {
    return myForceFetchResultValid;
  }
}
