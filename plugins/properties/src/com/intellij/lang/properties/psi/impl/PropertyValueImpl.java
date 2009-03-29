package com.intellij.lang.properties.psi.impl;

import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class PropertyValueImpl extends LeafPsiElement {
  public PropertyValueImpl(IElementType type, CharSequence text) {
    super(type, text);
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, PropertyValueImpl.class);
  }

  public PsiReference getReference() {
    PsiReference[] references = getReferences();
    return references.length == 0 ? null : references[0];
  }

  @NonNls
  public String toString() {
    return "Property value: " + getText();
  }
}
