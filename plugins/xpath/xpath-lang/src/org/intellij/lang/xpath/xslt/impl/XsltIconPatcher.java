// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.ide.FileIconPatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public class XsltIconPatcher implements FileIconPatcher {

  @Override
  public @NotNull Icon patchIcon(@NotNull Icon icon, @NotNull VirtualFile file, int flags, @Nullable Project project) {
    if (project == null) return icon;

    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile != null && XsltSupport.isXsltFile(psiFile)) {
      return XsltSupport.createXsltIcon(icon);
    }
    return icon;
  }

}
