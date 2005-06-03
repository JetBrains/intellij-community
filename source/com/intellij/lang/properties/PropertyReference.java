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
public class PropertyReference implements PsiPolyVariantReference {
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
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    List<Property> properties = PropertiesUtil.findPropertiesByKey(getElement().getProject(), myKey);
    final ResolveResult[] result = new ResolveResult[properties.size()];
    for (int i = 0; i < properties.size(); i++) {
      final Property property = properties.get(i);
      result[i] = new ResolveResult() {
        public PsiElement getElement() {
          return property;
        }

        public boolean isValidResult() {
          return property.isValid();
        }
      };
    }
    return result;
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
      List<Property> properties = propertiesFile.getProperties();
      for (Property property : properties) {
        variants.add(property.getKey());
      }
    }
    return variants.toArray(new Object[variants.size()]);
  }
  
  public List<Property> suggestProperties() {
    return PropertiesUtil.findPropertiesByKey(getElement().getProject(), myKey);
  }

  public boolean isSoft() {
    return false;
  }
}
