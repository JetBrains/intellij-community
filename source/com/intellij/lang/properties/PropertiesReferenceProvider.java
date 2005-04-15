package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
class PropertiesReferenceProvider implements PsiReferenceProvider {
  public PsiReference[] getReferencesByElement(PsiElement element) {
    PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
    final Object value = literalExpression.getValue();
    if (value instanceof String) {
      String text = (String)value;
      //PsiClass aClass = element.getManager().findClass(text, GlobalSearchScope.allScope(element.getProject()));
      //return new PsiReference[]{new LightClassReference(element.getManager(), element.getText(), aClass)};
      List<Property> properties = PropertiesUtil.findPropertiesByKey(element.getProject(), text);
      List<PsiReference> references = new ArrayList<PsiReference>(properties.size());
      for (int i = 0; i < properties.size(); i++) {
        Property property = properties.get(i);
        PsiReference reference = new PropertyReference(property, literalExpression);
        references.add(reference);
      }
      return references.toArray(new PsiReference[references.size()]);
    }

    return PsiReference.EMPTY_ARRAY;
  }

  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }

}
