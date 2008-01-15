package com.intellij.lang.properties;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class ResourceBundleReferenceProvider implements PsiReferenceProvider {

  protected boolean mySoft;

  public ResourceBundleReferenceProvider() {
    this(false);    
  }

  public ResourceBundleReferenceProvider(boolean soft) {
    mySoft = soft;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
        ResourceBundleReference reference = new ResourceBundleReference(element, mySoft);
        return new PsiReference[] { reference };
  }

}
