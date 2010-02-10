package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLScalarText;

/**
 * @author oleg
 */
public class YAMLScalarTextImpl extends YAMLPsiElementImpl implements YAMLScalarText {
  public YAMLScalarTextImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML scalar text";
  }

  @NotNull
  /**
   * Provide reference contributor with given method registerReferenceProviders implementation:
   * registrar.registerReferenceProvider(PlatformPatterns.psiElement(YAMLKeyValue.class), ReferenceProvider);
   */
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, YAMLScalarText.class);
  }
}