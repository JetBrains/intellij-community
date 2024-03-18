// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.projectView;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler;
import com.intellij.uiDesigner.GuiFormFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;


public class FormMoveProvider extends MoveHandlerDelegate {
  private static final Logger LOG = Logger.getInstance(FormMoveProvider.class);

  @Override
  public boolean canMove(DataContext dataContext) {
    Form[] forms = Form.DATA_KEY.getData(dataContext);
    return forms != null && forms.length > 0;
  }

  @Override
  public boolean isValidTarget(PsiElement psiElement, PsiElement[] sources) {
    return MoveFilesOrDirectoriesHandler.isValidTarget(psiElement);
  }

  @Override
  public boolean canMove(PsiElement[] elements, final @Nullable PsiElement targetContainer, @Nullable PsiReference reference) {
    return false;
  }

  @Override
  public void collectFilesOrDirsFromContext(DataContext dataContext, Set<PsiElement> filesOrDirs) {
    Form[] forms = Form.DATA_KEY.getData(dataContext);
    LOG.assertTrue(forms != null);
    PsiClass[] classesToMove = new PsiClass[forms.length];
    PsiFile[] filesToMove = new PsiFile[forms.length];
    for(int i=0; i<forms.length; i++) {
      classesToMove [i] = forms [i].getClassToBind();
      if (classesToMove[i] != null) {
        final PsiFile containingFile = classesToMove[i].getContainingFile();
        if (containingFile != null) {
          filesOrDirs.add(containingFile);
        }
      }
      filesToMove [i] = forms [i].getFormFiles() [0];
      if (filesToMove[i] != null) {
        filesOrDirs.add(filesToMove[i]);
      }
    }
  }


  @Override
  public boolean isMoveRedundant(PsiElement source, PsiElement target) {
    if (source instanceof PsiFile && source.getParent() == target) {
      final VirtualFile virtualFile = ((PsiFile)source).getVirtualFile();
      if (virtualFile != null && virtualFile.getFileType() instanceof GuiFormFileType) {
        return true;
      }
    }
    return super.isMoveRedundant(source, target);
  }

  @Override
  public boolean supportsLanguage(@NotNull Language language) {
    return false;  // only available in project view
  }
}
