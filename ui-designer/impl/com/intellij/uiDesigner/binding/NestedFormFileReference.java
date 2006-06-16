/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.binding;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class NestedFormFileReference extends ReferenceInForm {
  public NestedFormFileReference(final PsiPlainTextFile file, TextRange range) {
    super(file, range);
  }

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
    final VirtualFile formFile = ModuleUtil.findResourceFileInDependents(module, getRangeText());
    if (formFile == null) {
      return null;
    }
    return PsiManager.getInstance(project).findFile(formFile);
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiPlainTextFile)) {
      throw new IncorrectOperationException();
    }

    updateRangeText(FormEditingUtil.buildResourceName((PsiPlainTextFile) element));
    return myFile;
  }
}
