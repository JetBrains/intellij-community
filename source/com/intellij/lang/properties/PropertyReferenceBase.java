package com.intellij.lang.properties;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public abstract class PropertyReferenceBase implements PsiPolyVariantReference, EmptyResolveMessageProvider {
  protected final String myKey;
  protected final PsiElement myElement;
  protected boolean mySoft;

  public PropertyReferenceBase(@NotNull String key, final boolean soft, @NotNull PsiElement element) {
    myKey = key;
    mySoft = soft;
    myElement = element;
  }

  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @NotNull
  protected String getKeyText() {
    return myKey;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PropertyReference other = (PropertyReference)o;

    return getElement() == other.getElement() && getKeyText().equals(other.getKeyText());
  }

  public int hashCode() {
    return getKeyText().hashCode();
  }

  @NotNull
  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return new TextRange(1,myElement.getTextLength()-1);
  }

  @NotNull
  public String getCanonicalText() {
    return myKey;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(myElement.getProject()).getElementFactory();

    if (myElement instanceof PsiLiteralExpression) {
      PsiExpression newExpression = factory.createExpressionFromText("\"" + newElementName + "\"", myElement);
      return myElement.replace(newExpression);
    }
    else {
      ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(myElement);
      assert manipulator != null;
      return manipulator.handleContentChange(myElement, getRangeInElement(), newElementName);
    }
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("not implemented");
  }

  public boolean isReferenceTo(PsiElement element) {
    return element instanceof Property && Comparing.strEqual(((Property)element).getUnescapedKey(), getKeyText());
  }

  protected void addKey(Object property, Set<Object> variants) {
    variants.add(property);
  }

  protected void addVariantsFromFile(final PropertiesFile propertiesFile, final Set<Object> variants) {
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

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final String key = getKeyText();

    List<Property> properties;
    final List<PropertiesFile> propertiesFiles = getPropertiesFiles();
    if (propertiesFiles == null) {
      properties = PropertiesUtil.findPropertiesByKey(getElement().getProject(), key);
    }
    else {
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

  @Nullable
  protected abstract List<PropertiesFile> getPropertiesFiles();

  public Object[] getVariants() {
    Set<Object> variants = new THashSet<Object>();
    List<PropertiesFile> propertiesFileList = getPropertiesFiles();
    if (propertiesFileList == null) {
      PsiManager psiManager = myElement.getManager();
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(psiManager.getProject()).getFileIndex();
      for (VirtualFile file : PropertiesFilesManager.getInstance().getAllPropertiesFiles()) {
        if (!file.isValid()) continue;
        if (!fileIndex.isInContent(file)) continue; //multiple opened projects
        PsiFile psiFile = psiManager.findFile(file);
        if (!(psiFile instanceof PropertiesFile)) continue;
        PropertiesFile propertiesFile = (PropertiesFile)psiFile;
        addVariantsFromFile(propertiesFile, variants);
      }
    }
    else {
      for (PropertiesFile propFile : propertiesFileList) {
        addVariantsFromFile(propFile, variants);
      }
    }
    return variants.toArray(new Object[variants.size()]);
  }
}
