/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class AttributeValueSelfReference extends BasicAttributeValueReference {
  public AttributeValueSelfReference(final PsiElement element) {
    super(element);
  }

  public AttributeValueSelfReference(final PsiElement element, int offset) {
    super(element, offset);
  }

  public AttributeValueSelfReference(final PsiElement element, TextRange range) {
    super(element, range);
  }

  @Nullable
  public PsiElement resolve() {
    return myElement;
  }

  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return true;
  }

  public boolean isReferenceTo(PsiElement element) {
    return super.isReferenceTo(element) ||
      ( element instanceof PsiNamedElement && // DOM node
        element.getNode() == null &&
        ((PsiNamedElement)element).getName().equals(getCanonicalText())
      );
  }
}
