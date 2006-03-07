package com.intellij.lang.properties;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.i18n.CreatePropertyFix;
import com.intellij.codeInsight.i18n.I18nUtil;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author cdr
 */
public class PropertyReference implements PsiPolyVariantReference, EmptyResolveMessageProvider, QuickFixProvider {
  private final String myKey;
  private final PsiElement myElement;
  @Nullable private final String myBundleName;

  public PropertyReference(String key, final PsiElement element, @Nullable final String bundleName) {
    myKey = key;
    myElement = element;
    myBundleName = bundleName;
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
    final String key = getKeyText();

    Collection<Property> properties;
    if (myBundleName != null) {
      final PropertiesFile propertiesFile = I18nUtil.propertiesFileByBundleName(myElement, myBundleName);
      if (propertiesFile != null) {
        properties = propertiesFile.findPropertiesByKey(key);
      }
      else {
        properties = new ArrayList<Property>();
      }
    }
    else {
      properties = PropertiesUtil.findPropertiesByKey(getElement().getProject(), key);
    }
    final ResolveResult[] result = new ResolveResult[properties.size()];
    int i = 0;
    for (Property property : properties) {
      result[i++] = new PsiElementResolveResult(property);
    }
    return result;
  }

  protected String getKeyText() {
    return myKey;
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
    return element instanceof Property && Comparing.strEqual(((Property)element).getKey(), getKeyText());
  }

  public Object[] getVariants() {
    Collection<VirtualFile> allPropertiesFiles = PropertiesFilesManager.getInstance().getAllPropertiesFiles();
    Set<String> variants = new THashSet<String>();
    if (myBundleName != null) {
      PropertiesFile propFile = I18nUtil.propertiesFileByBundleName(myElement, myBundleName);
      addVariantsFromFile(propFile, variants);
    }
    else {
      PsiManager psiManager = myElement.getManager();
      for (VirtualFile file : allPropertiesFiles) {
        if (!file.isValid()) continue;
        PropertiesFile propertiesFile = (PropertiesFile)psiManager.findFile(file);
        addVariantsFromFile(propertiesFile, variants);
      }
    }
    return variants.toArray(new Object[variants.size()]);
  }

  protected void addKey(String key, Set<String> variants) {
    variants.add(key);
  }

  private void addVariantsFromFile(final PropertiesFile propertiesFile, final Set<String> variants) {
    if (propertiesFile == null) return;
    List<Property> properties = propertiesFile.getProperties();
    for (Property property : properties) {
      addKey(property.getKey(), variants);
    }
  }

  public boolean isSoft() {
    return false;
  }

  public String getUnresolvedMessagePattern() {
    return PropertiesBundle.message("unresolved.property.key");
  }

  public void registerQuickfix(HighlightInfo info, PsiReference reference) {
    CreatePropertyFix fix = new CreatePropertyFix(myElement, myKey, myBundleName);
    QuickFixAction.registerQuickFixAction(info, fix);
  }
}
