package com.intellij.structuralsearch.equivalence;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.structuralsearch.StructuralSearchProfileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class EquivalenceDescriptorProvider {
  public static final ExtensionPointName<EquivalenceDescriptorProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.equivalenceDescriptorProvider");

  public abstract boolean isMyContext(@NotNull PsiElement context);

  @Nullable
  public abstract EquivalenceDescriptor buildDescriptor(@NotNull PsiElement element);

  // delimeter of SSR vars when sequential occurence is applicable; by default - only whitespaces
  public boolean canBeVariableDelimeter(@NotNull PsiElement element) {
    if (!(element instanceof LeafElement)) {
      return false;
    }

    final IElementType elementType = ((LeafElement)element).getElementType();
    return getVariableDelimeters().contains(elementType);
  }

  @NotNull
  public TokenSet getVariableDelimeters() {
    return TokenSet.EMPTY;
  }

  public TokenSet getLiterals() {
    return TokenSet.EMPTY;
  }

  public int getNodeCost(@NotNull PsiElement element) {
    return getDefaultNodeCost(element);
  }

  // by default only PsiWhitespace ignored
  public TokenSet getIgnoredTokens() {
    return TokenSet.EMPTY;
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
    for (EquivalenceDescriptorProvider descriptorProvider : EquivalenceDescriptorProvider.EP_NAME.getExtensions()) {
      if (descriptorProvider.isMyContext(context)) {
        return descriptorProvider;
      }
    }
    return null;
  }
}
