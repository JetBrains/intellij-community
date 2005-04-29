package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;

import java.util.Collection;
import java.util.List;
import java.util.Set;

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
    PsiElementFactory factory = myLiteralExpression.getManager().getElementFactory();
    PsiExpression newExpression = factory.createExpressionFromText("\"" + newElementName + "\"", myLiteralExpression);
    return myLiteralExpression.replace(newExpression);
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("not implemented");
  }

  public boolean isReferenceTo(PsiElement element) {
    return element instanceof Property && Comparing.strEqual(((Property)element).getKey(), myKey);
  }

  public Object[] getVariants() {
    Collection<VirtualFile> allPropertiesFiles = PropertiesFilesManager.getInstance().getAllPropertiesFiles();
    Set<String> variants = new THashSet<String>();
    PsiManager psiManager = myLiteralExpression.getManager();
    for (VirtualFile file : allPropertiesFiles) {
      if (!file.isValid()) continue;
      PropertiesFile propertiesFile = (PropertiesFile)psiManager.findFile(file);
      if (propertiesFile == null) continue;
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
