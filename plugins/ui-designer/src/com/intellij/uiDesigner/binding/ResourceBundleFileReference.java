// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesUtilBase;
import com.intellij.lang.properties.ResourceBundleManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
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
public final class ResourceBundleFileReference extends ReferenceInForm {
  private static final Logger LOG = Logger.getInstance(ResourceBundleFileReference.class);

  public ResourceBundleFileReference(final PsiPlainTextFile file, TextRange bundleNameRange) {
    super(file, bundleNameRange);
  }

  @Override
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
    PropertiesFile propertiesFile = PropertiesUtilBase.getPropertiesFile(getRangeText(), module, null);
    return propertiesFile == null ? null : propertiesFile.getContainingFile();
  }

  @Override
  public boolean isReferenceTo(final @NotNull PsiElement element) {
    if (!(element instanceof PropertiesFile)) return false;
    String baseName = ResourceBundleManager.getInstance(element.getProject()).getFullName((PropertiesFile)element);
    if (baseName == null) return false;
    baseName = baseName.replace('.', '/');
    final String rangeText = getRangeText();
    return rangeText.equals(baseName);
  }

  @Override
  public PsiElement handleElementRename(final @NotNull String newElementName) {
    return handleFileRename(newElementName, PropertiesFileType.DOT_DEFAULT_EXTENSION, false);
  }

  @Override
  public PsiElement bindToElement(final @NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PropertiesFile propertyFile)) {
      throw new IncorrectOperationException();
    }

    final String bundleName = FormReferenceProvider.getBundleName(propertyFile);
    LOG.assertTrue(bundleName != null);
    updateRangeText(bundleName);
    return myFile;
  }
}
