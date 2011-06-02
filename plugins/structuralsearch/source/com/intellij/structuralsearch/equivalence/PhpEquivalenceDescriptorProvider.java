package com.intellij.structuralsearch.equivalence;

import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.GroupStatement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class PhpEquivalenceDescriptorProvider extends EquivalenceDescriptorProvider {
  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage().isKindOf(PhpLanguage.INSTANCE);
  }

  @Override
  public EquivalenceDescriptor buildDescriptor(@NotNull PsiElement e) {
    final EquivalenceDescriptorBuilder b = new EquivalenceDescriptorBuilder();

    if (e instanceof GroupStatement) {
      return b.codeBlock(((GroupStatement)e).getStatements());
    }
    return null;
  }
}