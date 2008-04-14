package com.intellij.util.xml.converters;

import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.converters.values.ClassValueConverter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.NotNull;

/**
 * User: Sergey.Vasiliev
 */
public class ClassValueConverterImpl extends ClassValueConverter {
  protected final JavaClassReferenceProvider myReferenceProvider =new JavaClassReferenceProvider();

  public ClassValueConverterImpl() {
    myReferenceProvider.setSoft(true);
    myReferenceProvider.setAllowEmpty(true);
  }

  @NotNull
  public PsiReference[] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    return myReferenceProvider.getReferencesByElement(element);
  }
}
