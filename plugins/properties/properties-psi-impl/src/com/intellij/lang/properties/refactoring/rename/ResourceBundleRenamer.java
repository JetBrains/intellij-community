// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.refactoring.rename;

import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.ResourceBundleManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.NameSuggester;
import org.jetbrains.annotations.NonNls;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleRenamer extends AutomaticRenamer {

  private final ResourceBundleManager myResourceBundleManager;
  private final String myOldBaseName;

  public ResourceBundleRenamer(final PropertiesFile propertiesFile, final String newName) {
    myResourceBundleManager = ResourceBundleManager.getInstance(propertiesFile.getProject());
    for (final PropertiesFile file : propertiesFile.getResourceBundle().getPropertiesFiles()) {
      if (file.equals(propertiesFile)) {
        continue;
      }
      final PsiFile containingFile = file.getContainingFile();
      myElements.add(containingFile);
    }
    myOldBaseName = myResourceBundleManager.getBaseName(propertiesFile.getContainingFile());
    suggestAllNames(propertiesFile.getName(), newName);
  }

  @Override
  protected String nameToCanonicalName(final @NonNls String name, final PsiNamedElement element) {
    return myResourceBundleManager.getBaseName((PsiFile)element);
  }

  @Override
  protected String canonicalNameToName(final @NonNls String canonicalName, final PsiNamedElement element) {
    final String oldCanonicalName = myResourceBundleManager.getBaseName((PsiFile)element);
    final String oldName = element.getName();
    assert oldName != null;
    return canonicalName + oldName.substring(oldCanonicalName.length());
  }

  @Override
  protected String suggestNameForElement(PsiNamedElement element, NameSuggester suggester, String newClassName, String oldClassName) {
    final String elementName = element.getName();
    if (elementName == null) {
      return newClassName;
    }
    final String baseClassNameSuffix = oldClassName.substring(myOldBaseName.length());
    if (baseClassNameSuffix.length() >= newClassName.length()) {
      return newClassName;
    }
    final String newBaseName = newClassName.substring(0, newClassName.length() - baseClassNameSuffix.length());
    return newBaseName + elementName.substring(myOldBaseName.length());
  }

  @Override
  public boolean isSelectedByDefault() {
    return true;
  }

  @Override
  public String getDialogTitle() {
    return PropertiesBundle.message("resource.bundle.renamer");
  }

  @Override
  public String getDialogDescription() {
    return PropertiesBundle.message("resource.bundle.renamer.dialog.description");
  }

  @Override
  public String entityName() {
    return PropertiesBundle.message("resource.bundle.renamer.entity.name");
  }
}
