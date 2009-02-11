package com.intellij.lang.properties;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
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
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        ResourceBundleReference reference = new ResourceBundleReference(element, mySoft);
        return new PsiReference[] { reference };
  }

}
