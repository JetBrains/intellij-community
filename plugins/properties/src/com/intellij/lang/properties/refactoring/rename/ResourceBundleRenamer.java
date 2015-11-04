/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  protected String nameToCanonicalName(@NonNls final String name, final PsiNamedElement element) {
    return myResourceBundleManager.getBaseName((PsiFile)element);
  }

  @Override
  protected String canonicalNameToName(@NonNls final String canonicalName, final PsiNamedElement element) {
    final String oldCanonicalName = myResourceBundleManager.getBaseName((PsiFile)element);
    final String oldName = element.getName();
    assert oldName != null;
    return canonicalName + oldName.substring(oldCanonicalName.length());
  }

  @Override
  protected String suggestNameForElement(PsiNamedElement element, NameSuggester suggester, String newClassName, String oldClassName) {
    final String elementName = element.getName();
    if (elementName == null) {
      return null;
    }
    final String baseClassNameSuffix = oldClassName.substring(myOldBaseName.length());
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
