// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DependentNSReference extends BasicAttributeValueReference {
  private final @NotNull URLReference myReference;
  private final boolean myForceFetchResultValid;

  public DependentNSReference(final PsiElement element, TextRange range, @NotNull URLReference ref) {
    this(element, range, ref, false);
  }

  public DependentNSReference(final PsiElement element,
                              TextRange range,
                              @NotNull URLReference ref,
                              boolean valid) {
    super(element, range);
    myReference = ref;
    myForceFetchResultValid = valid;
  }

  @Override
  public @Nullable PsiElement resolve() {
    final String canonicalText = getCanonicalText();
    final PsiFile file = ExternalResourceManager.getInstance().getResourceLocation(canonicalText, myElement.getContainingFile(), null);
    if (file != null) return file;
    PsiElement element = myReference.resolve();
    if (element == null && !myForceFetchResultValid && !XmlUtil.isUrlText(canonicalText, myElement.getProject())) return myElement;  // file reference will highlight it
    return element;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  public boolean isForceFetchResultValid() {
    return myForceFetchResultValid;
  }

  public @NotNull URLReference getNamespaceReference() {
    return myReference;
  }
}
