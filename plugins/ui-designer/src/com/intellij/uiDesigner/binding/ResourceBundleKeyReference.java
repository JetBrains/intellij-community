/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesUtilBase;
import com.intellij.lang.properties.ResourceBundleManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
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
    myBundleName = bundleName;
  }

  public PsiElement resolve() {
    final Project project = myFile.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile formVirtualFile = myFile.getVirtualFile();
    if (formVirtualFile == null) {
      return null;
    }
    final Module module = fileIndex.getModuleForFile(formVirtualFile);
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

  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof IProperty)) {
      throw new IncorrectOperationException();
    }
    updateRangeText(((IProperty)element).getUnescapedKey());
    return myFile;
  }

  public boolean isReferenceTo(final PsiElement element) {
    if (!(element instanceof IProperty)) {
      return false;
    }
    IProperty property = (IProperty) element;
    String baseName = ResourceBundleManager.getInstance(element.getProject()).getFullName(property.getPropertiesFile());
    return baseName != null && myBundleName.equals(baseName.replace('.', '/')) && getRangeText().equals(property.getUnescapedKey());
  }
}
