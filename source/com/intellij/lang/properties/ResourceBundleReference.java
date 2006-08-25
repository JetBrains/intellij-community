package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author yole
 */
public class ResourceBundleReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
  private String myBundleName;
  @NonNls private static final String PROPERTIES = ".properties";

  public ResourceBundleReference(final PsiElement element) {
    super(element);
    myBundleName = getValue();
  }

  @Nullable public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @NotNull public ResolveResult[] multiResolve(final boolean incompleteCode) {
    PropertiesReferenceManager referenceManager = myElement.getProject().getComponent(PropertiesReferenceManager.class);
    final Module module = ModuleUtil.findModuleForPsiElement(myElement);
    if (module == null) {
      return ResolveResult.EMPTY_ARRAY;
    }
    List<PropertiesFile> propertiesFiles = referenceManager.findPropertiesFiles(module, myBundleName);
    final ResolveResult[] result = new ResolveResult[propertiesFiles.size()];
    for(int i=0; i<propertiesFiles.size(); i++) {
      PropertiesFile file = propertiesFiles.get(i);
      result[i] = new PsiElementResolveResult(file);
    }
    return result;
  }

  public String getCanonicalText() {
    return myBundleName;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    if (newElementName.endsWith(PROPERTIES)) {
      newElementName = newElementName.substring(0, newElementName.lastIndexOf(PROPERTIES));
    }

    final int index = myBundleName.lastIndexOf('.');
    if (index != -1) {
      newElementName = myBundleName.substring(0, index) + "." + newElementName;
    }

    return super.handleElementRename(newElementName);
  }

  public PsiElement bindToElement(final PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PropertiesFile)) {
      throw new IncorrectOperationException();
    }
    final String name = PropertiesUtil.getFullName((PropertiesFile)element);
    return super.handleElementRename(name);
  }


  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PropertiesFile) {
      final String name = PropertiesUtil.getFullName((PropertiesFile)element);
      if (name != null && name.equals(myBundleName)) {
        return true;
      }
    }
    return false;
  }

  public Object[] getVariants() {
    PropertiesReferenceManager referenceManager = myElement.getProject().getComponent(PropertiesReferenceManager.class);
    final Module module = ModuleUtil.findModuleForPsiElement(myElement);
    return referenceManager.getPropertyFileBaseNames(module);
  }
}
