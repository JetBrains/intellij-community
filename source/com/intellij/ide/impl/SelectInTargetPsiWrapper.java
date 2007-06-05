package com.intellij.ide.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;


public abstract class SelectInTargetPsiWrapper implements SelectInTarget {
  protected final Project myProject;

  protected SelectInTargetPsiWrapper(@NotNull final Project project) {
    myProject = project;
  }

  public abstract String toString();

  protected abstract boolean canSelect(PsiFileSystemItem file);

  public final boolean canSelect(SelectInContext context) {
    VirtualFile virtualFile = context.getVirtualFile();
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    final PsiFileSystemItem psiFile;
    if (document != null) {
      psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    }
    else if (context.getSelectorInFile() instanceof PsiFile) {
      psiFile = (PsiFile)context.getSelectorInFile();
    }
    else if (virtualFile.isDirectory()) {
      psiFile = PsiManager.getInstance(myProject).findDirectory(virtualFile);
    }
    else {
      psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
    }
    return psiFile != null && canSelect(psiFile) || canWorkWithCustomObjects();
  }

  public final void selectIn(SelectInContext context, final boolean requestFocus) {
    final Object selector = context.getSelectorInFile();
    if (selector instanceof PsiElement) {
      select(((PsiElement)selector).getOriginalElement(), requestFocus);
    } else {
      select(selector, context.getVirtualFile(), requestFocus);
    }
  }

  protected abstract void select(final Object selector, VirtualFile virtualFile, final boolean requestFocus);

  protected abstract boolean canWorkWithCustomObjects();

  protected abstract void select(PsiElement element, boolean requestFocus);
}
