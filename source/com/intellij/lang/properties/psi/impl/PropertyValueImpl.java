package com.intellij.lang.properties.psi.impl;

import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class PropertyValueImpl extends LeafPsiElement {
  public PropertyValueImpl(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    super(type, buffer, startOffset, endOffset, table);
  }

  @NotNull
  public PsiReference[] getReferences() {
    String text = getText();
    String[] words = text.split("\\s");
    if (words.length == 0) return PsiReference.EMPTY_ARRAY;
    return new JavaClassReferenceProvider(){
      public boolean isSoft() {
        return true;
      }
    }.getReferencesByString(words[0], this, 0);
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
