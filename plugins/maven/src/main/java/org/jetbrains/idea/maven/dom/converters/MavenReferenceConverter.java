package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

public abstract class MavenReferenceConverter<T> extends Converter<T> implements CustomReferenceConverter<T> {
  @NotNull
  public PsiReference[] createReferences(GenericDomValue value, PsiElement element, ConvertContext context) {
    String text = value.getStringValue();
    TextRange range = ElementManipulators.getValueTextRange(element);
    return new PsiReference[]{createReference(element, text, range)};
  }

  protected abstract PsiReference createReference(PsiElement element, String text, TextRange range
  );
}
