package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class ResourceBundleReference implements PsiPolyVariantReference {
  private PsiLiteralExpression myElement;
  private String myBundleName;

  public ResourceBundleReference(final PsiLiteralExpression element, String bundleName) {
    myElement = element;
    myBundleName = bundleName;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return new TextRange(1,myElement.getTextLength()-1);
  }

  @Nullable public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @NotNull public ResolveResult[] multiResolve(final boolean incompleteCode) {
    PropertiesReferenceManager referenceManager = myElement.getProject().getComponent(PropertiesReferenceManager.class);
    final Module module = ModuleUtil.findModuleForPsiElement(myElement);
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
    PsiElementFactory factory = myElement.getManager().getElementFactory();
    PsiExpression newExpression = factory.createExpressionFromText("\"" + newElementName + "\"", myElement);
    return myElement.replace(newExpression);
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("not implemented");
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PropertiesFile) {
      final VirtualFile virtualFile = ((PropertiesFile)element).getVirtualFile();
      if (virtualFile != null && PropertiesUtil.getBaseName(virtualFile).equals(myBundleName)) {
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

  public boolean isSoft() {
    return false;
  }
}
