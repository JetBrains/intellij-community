// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.binding;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ResourceFileUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ResourceFileReference extends ReferenceInForm {
  public ResourceFileReference(final PsiPlainTextFile file, TextRange range) {
    super(file, range);
  }

  @Override
  @Nullable
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
    final VirtualFile formFile = ResourceFileUtil.findResourceFileInDependents(module, getRangeText());
    if (formFile == null) {
      return null;
    }
    return PsiManager.getInstance(project).findFile(formFile);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiFile)) { //should be icon file or nested form
      throw new IncorrectOperationException();
    }

    updateRangeText(FormEditingUtil.buildResourceName((PsiFile)element));
    return myFile;
  }

  @Override
  public PsiElement handleElementRename(@NotNull final String newElementName) {
    return handleFileRename(newElementName, "", true);
  }
}
