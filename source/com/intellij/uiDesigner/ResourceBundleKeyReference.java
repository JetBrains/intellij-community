/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner;

import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.module.Module;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.util.IncorrectOperationException;

import java.net.URL;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 5, 2005
 */
final class ResourceBundleKeyReference extends ReferenceInForm {
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
    URL resource = new ResourceBundleLoader(module).getResource(myBundleName + ".properties");
    if (resource == null) {
      return null;
    }
    final VirtualFile vFile = VfsUtil.findFileByURL(resource);
    if (vFile == null) {
      return null;
    }
    final PsiFile propertrtiesFile = PsiManager.getInstance(project).findFile(vFile);
    if (!(propertrtiesFile instanceof PropertiesFile)) {
      return null;
    }
    return ((PropertiesFile)propertrtiesFile).findPropertyByKey(getRangeText());
  }

  public PsiElement bindToElement(final PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof Property)) {
      throw new IncorrectOperationException();
    }
    updateRangeText(((Property)element).getKey());
    return myFile;
  }
}
