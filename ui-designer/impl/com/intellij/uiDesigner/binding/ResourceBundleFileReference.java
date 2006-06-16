/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.PropertiesUtil;
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
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 5, 2005
 */
public final class ResourceBundleFileReference extends ReferenceInForm {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.ResourceBundleFileReference");

  @NonNls
  static final String PROPERTIES_EXTENSION = ".properties";

  public ResourceBundleFileReference(final PsiPlainTextFile file, TextRange bundleNameRange) {
    super(file, bundleNameRange);
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
    return PropertiesUtil.getPropertiesFile(getRangeText(), module);
  }

  public PsiElement handleElementRename(final String newElementName){
    final String currentName = getRangeText();
    final int slashIndex = currentName.lastIndexOf('/');
    final String prefix = currentName.substring(0, slashIndex);
    final String baseName = newElementName.endsWith(PROPERTIES_EXTENSION)?
                            newElementName.substring(0, newElementName.length() - PROPERTIES_EXTENSION.length()) :
                            newElementName;
    return super.handleElementRename(prefix + "/" + baseName);
  }

  public PsiElement bindToElement(final PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PropertiesFile)) {
      throw new IncorrectOperationException();
    }

    final PropertiesFile propertyFile = ((PropertiesFile)element);
    final String bundleName = FormReferenceProvider.getBundleName(propertyFile);
    LOG.assertTrue(bundleName != null);
    updateRangeText(bundleName);
    return myFile;
  }
}
