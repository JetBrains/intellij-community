package org.jetbrains.idea.maven.plugins.api;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;

public abstract class MavenCompletionReferenceProvider implements MavenParamReferenceProvider {

  protected abstract Object[] getVariants(@NotNull PsiReferenceBase reference);

  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                               @NotNull MavenDomConfiguration domCfg,
                                               @NotNull ProcessingContext context) {
    return new PsiReference[] {
      new PsiReferenceBase<>(element, true) {
        @Override
        public PsiElement resolve() {
          return null;
        }

        @Override
        public Object @NotNull [] getVariants() {
          return MavenCompletionReferenceProvider.this.getVariants(this);
        }
      }
    };
  }
}
