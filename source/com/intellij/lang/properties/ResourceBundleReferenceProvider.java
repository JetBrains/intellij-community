package com.intellij.lang.properties;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.patterns.MatchingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class ResourceBundleReferenceProvider extends PsiReferenceProvider {

  protected boolean mySoft;

  public ResourceBundleReferenceProvider() {
    this(false);    
  }

  public ResourceBundleReferenceProvider(boolean soft) {
    mySoft = soft;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final MatchingContext matchingContext) {
        ResourceBundleReference reference = new ResourceBundleReference(element, mySoft);
        return new PsiReference[] { reference };
  }

}
