package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author cdr
 */
public class PropertyReference implements PsiPolyVariantReference {
  private final String myKey;
  private final PsiElement myElement;

  public PropertyReference(String key, final PsiElement element) {
    myKey = key;
    myElement = element;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return new TextRange(1,myElement.getTextLength()-1);
  }

  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    Collection<Property> properties = PropertiesUtil.findPropertiesByKey(getElement().getProject(), myKey);
    final ResolveResult[] result = new ResolveResult[properties.size()];
    int i = 0;
    for (Property property : properties) {
      result[i++] = new PsiElementResolveResult(property);
    }
    return result;
  }

  public String getCanonicalText() {
    return myKey;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElementFactory factory = myElement.getManager().getElementFactory();

    if (myElement instanceof PsiLiteralExpression) {
      PsiExpression newExpression = factory.createExpressionFromText("\"" + newElementName + "\"", myElement);
      return myElement.replace(newExpression);
    } else if (myElement instanceof XmlAttributeValue) {
      return ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myElement).handleContentChange(
        myElement,
        getRangeInElement(),
        newElementName
      );
    } else {
      return null;
    }
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
    PsiManager psiManager = myElement.getManager();
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

  public boolean isSoft() {
    return false;
  }
}
