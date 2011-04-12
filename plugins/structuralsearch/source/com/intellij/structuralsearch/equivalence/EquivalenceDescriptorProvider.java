package com.intellij.structuralsearch.equivalence;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.structuralsearch.StructuralSearchProfileImpl;
import com.intellij.structuralsearch.equivalence.js.JSEquivalenceDescriptorProvider;
import com.intellij.structuralsearch.equivalence.xml.XmlEquivalenceDescriptorProvider;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class EquivalenceDescriptorProvider {

  public static final EquivalenceDescriptorProvider[] DESCRIPTOR_PROVIDERS = new EquivalenceDescriptorProvider[] {
    new JSEquivalenceDescriptorProvider(),
    new XmlEquivalenceDescriptorProvider(),
  };

  public abstract boolean isMyContext(@NotNull PsiElement context);

  @Nullable
  public abstract EquivalenceDescriptor buildDescriptor(@NotNull PsiElement element);

  // delimeter of SSR vars when sequential occurence is applicable; by default - only whitespaces
  public boolean canBeVariableDelimeter(@NotNull PsiElement element) {
    if (!(element instanceof LeafElement)) {
      return false;
    }

    final IElementType elementType = ((LeafElement)element).getElementType();
    return ArrayUtil.find(getVariableDelimeters(), elementType) >= 0;
  }

  @NotNull
  public IElementType[] getVariableDelimeters() {
    return IElementType.EMPTY_ARRAY;
  }

  public TokenSet getLiterals() {
    return TokenSet.EMPTY;
  }

  public int getNodeCost(@NotNull PsiElement element) {
    return getDefaultNodeCost(element);
  }

  public static int getDefaultNodeCost(PsiElement element) {
    if (!(element instanceof LeafElement)) {
      return 0;
    }

    if (StructuralSearchProfileImpl.containsOnlyDelimeters(element.getText())) {
      return 0;
    }

    return 1;
  }

  @Nullable
  public static EquivalenceDescriptorProvider getInstance(@NotNull PsiElement context) {
    for (EquivalenceDescriptorProvider descriptorProvider : DESCRIPTOR_PROVIDERS) {
      if (descriptorProvider.isMyContext(context)) {
        return descriptorProvider;
      }
    }
    return null;
  }
}
