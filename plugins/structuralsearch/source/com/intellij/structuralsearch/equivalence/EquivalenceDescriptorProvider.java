package com.intellij.structuralsearch.equivalence;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
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

  // by default only PsiWhitespace ignored
  public TokenSet getIgnoredTokens() {
    return TokenSet.EMPTY;
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
