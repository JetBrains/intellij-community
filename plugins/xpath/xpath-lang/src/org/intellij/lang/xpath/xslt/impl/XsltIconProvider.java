// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.ide.FileIconPatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.intellij.lang.xpath.xslt.XsltSupport;

import javax.swing.*;

public class XsltIconProvider implements FileIconPatcher {

  @Override
  public Icon patchIcon(Icon baseIcon, VirtualFile file, int flags, Project project) {
    if (project == null) return baseIcon;

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile != null && XsltSupport.isXsltFile(psiFile)) {
      return XsltSupport.createXsltIcon(baseIcon);
    }
    return baseIcon;
  }

}
