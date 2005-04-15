package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.List;

/**
 * @author cdr
 */
class PropertyReference implements PsiReference {
  private final String myKey;
  private final PsiLiteralExpression myLiteralExpression;

  public PropertyReference(String key, final PsiLiteralExpression literalExpression) {
    myKey = key;
    myLiteralExpression = literalExpression;
  }

  public PsiElement getElement() {
    return myLiteralExpression;
  }

  public TextRange getRangeInElement() {
    return new TextRange(1,myLiteralExpression.getTextLength()-1);
  }

  public PsiElement resolve() {
    List<Property> properties = PropertiesUtil.findPropertiesByKey(getElement().getProject(), myKey);

    return properties.size() == 0 ? null : properties.get(0);
  }

  public String getCanonicalText() {
    return myKey;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException("not implemented");
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("not implemented");
  }

  public boolean isReferenceTo(PsiElement element) {
    return element instanceof Property && Comparing.strEqual(((Property)element).getKey(), myKey);
  }

  public Object[] getVariants() {
    PropertiesReferenceManager referenceManager = PropertiesReferenceManager.getInstance(getElement().getProject());
    Collection<PropertiesFile> allPropertiesFiles = referenceManager.getAllPropertiesFiles();
    Set<String> variants = new THashSet<String>();
    for (Iterator<PropertiesFile> iterator = allPropertiesFiles.iterator(); iterator.hasNext();) {
      PropertiesFile propertiesFile = iterator.next();
      Property[] properties = propertiesFile.getProperties();
      for (int i = 0; i < properties.length; i++) {
        Property property = properties[i];
        variants.add(property.getKey());
      }
    }
    return variants.toArray(new Object[variants.size()]);
  }

  public boolean isSoft() {
    return false;
  }
}
