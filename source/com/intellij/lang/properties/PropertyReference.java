package com.intellij.lang.properties;

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInspection.i18n.CreatePropertyFix;
import com.intellij.codeInspection.i18n.I18nUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ElementManipulator;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author cdr
 */
public class PropertyReference extends GenericReference implements PsiPolyVariantReference, QuickFixProvider {
  private final String myKey;
  private final PsiElement myElement;
  @Nullable private final String myBundleName;
  private boolean mySoft;

  public PropertyReference(String key, final PsiElement element, @Nullable final String bundleName, final boolean soft) {
    super(getPropertiesProvider(element.getProject()));
    myKey = key;
    myElement = element;
    myBundleName = bundleName;
    mySoft = soft;
  }

  private static PsiReferenceProvider getPropertiesProvider(final Project project) {
    return ReferenceProvidersRegistry.getInstance(project).getProviderByType(ReferenceProvidersRegistry.PROPERTIES_FILE_KEY_PROVIDER);
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

    List<Property> properties;
    if (myBundleName == null) {
      properties = PropertiesUtil.findPropertiesByKey(getElement().getProject(), key);
    }
    else {
      final List<PropertiesFile> propertiesFiles = I18nUtil.propertiesFilesByBundleName(myElement, myBundleName);
      properties = new ArrayList<Property>();
      for (PropertiesFile propertiesFile : propertiesFiles) {
        properties.addAll(propertiesFile.findPropertiesByKey(key));
      }
    }
    // put default properties file first
    Collections.sort(properties, new Comparator<Property>() {
      public int compare(final Property o1, final Property o2) {
        String name1 = o1.getContainingFile().getName();
        String name2 = o2.getContainingFile().getName();
        return Comparing.compare(name1, name2);
      }
    });
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
    }
    else {
      ElementManipulator<PsiElement> manipulator = ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myElement);
      assert manipulator != null;
      return manipulator.handleContentChange(myElement, getRangeInElement(), newElementName);
    }
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("not implemented");
  }

  public boolean isReferenceTo(PsiElement element) {
    return element instanceof Property && Comparing.strEqual(((Property)element).getKey(), getKeyText());
  }

  public Object[] getVariants() {
    Set<Object> variants = new THashSet<Object>();
    if (myBundleName == null) {
      PsiManager psiManager = myElement.getManager();
      for (VirtualFile file : PropertiesFilesManager.getInstance().getAllPropertiesFiles()) {
        if (!file.isValid()) continue;
        PsiFile psiFile = psiManager.findFile(file);
        if (!(psiFile instanceof PropertiesFile)) continue;
        PropertiesFile propertiesFile = (PropertiesFile)psiFile;
        addVariantsFromFile(propertiesFile, variants);
      }
    }
    else {
      PropertiesFile propFile = I18nUtil.propertiesFileByBundleName(myElement, myBundleName);
      addVariantsFromFile(propFile, variants);
    }
    return variants.toArray(new Object[variants.size()]);
  }

  protected void addKey(Object property, Set<Object> variants) {
    variants.add(property);
  }

  private void addVariantsFromFile(final PropertiesFile propertiesFile, final Set<Object> variants) {
    if (propertiesFile == null) return;
    List<Property> properties = propertiesFile.getProperties();
    for (Property property : properties) {
      addKey(property, variants);
    }
  }

  protected void setSoft(final boolean soft) {
    mySoft = soft;
  }

  public boolean isSoft() {
    return mySoft;
  }

  public String getUnresolvedMessagePattern() {
    return PropertiesBundle.message("unresolved.property.key");
  }

  public void registerQuickfix(HighlightInfo info, PsiReference reference) {
    CreatePropertyFix fix = new CreatePropertyFix(myElement, myKey, myBundleName);
    QuickFixAction.registerQuickFixAction(info, fix);
  }

  /////////////////////////

  public PsiElement getContext() {
    return null;
  }

  public PsiReference getContextReference() {
    return null;
  }

  public ReferenceType getType() {
    return null;
  }

  public ReferenceType getSoftenType() {
    return null;
  }

  public boolean needToCheckAccessibility() {
    return false;
  }
}
