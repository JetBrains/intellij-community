// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesUtilBase;
import com.intellij.lang.properties.ResourceBundleManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class ResourceBundleKeyReference extends ReferenceInForm {
  private final String myBundleName;

  public ResourceBundleKeyReference(final PsiPlainTextFile file, String bundleName, TextRange keyNameRange) {
    super(file, keyNameRange);
    myBundleName = bundleName.replace('/', '.');
  }

  @Override
  public PsiElement resolve() {
    final Module module = ModuleUtilCore.findModuleForFile(myFile);
    if (module == null) {
      return null;
    }
    final PropertiesFile propertiesFile = PropertiesUtilBase.getPropertiesFile(myBundleName, module, null);
    if (propertiesFile == null) {
      return null;
    }
    IProperty property = propertiesFile.findPropertyByKey(getRangeText());
    return property == null ? null : property.getPsiElement();
  }

  @Override
  public PsiElement bindToElement(final @NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof IProperty)) {
      throw new IncorrectOperationException();
    }
    updateRangeText(((IProperty)element).getUnescapedKey());
    return myFile;
  }

  @Override
  public boolean isReferenceTo(final @NotNull PsiElement element) {
    if (!(element instanceof IProperty property)) {
      return false;
    }
    String baseName = ResourceBundleManager.getInstance(element.getProject()).getFullName(property.getPropertiesFile());
    return baseName != null && myBundleName.equals(baseName) && getRangeText().equals(property.getUnescapedKey());
  }
}
