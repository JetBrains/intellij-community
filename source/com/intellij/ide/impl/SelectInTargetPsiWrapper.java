package com.intellij.ide.impl;

import com.intellij.ide.SelectInTarget;
import com.intellij.ide.SelectInContext;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;


public abstract class SelectInTargetPsiWrapper implements SelectInTarget {
  protected final Project myProject;

  protected SelectInTargetPsiWrapper(final Project project) {
    myProject = project;
  }

  public abstract String toString();

  protected abstract boolean canSelect(PsiFile file);

  public final boolean canSelect(SelectInContext context) {
    final Document document = FileDocumentManager.getInstance().getDocument(context.getVirtualFile());
    if (document != null) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      if (psiFile != null) {
        if (canSelect(psiFile)){
          return true;
        }
      }
    }

    if (!canWorkWithCustomObjects()) {
      return false;
    } else {
      return true;
    }
  }

  public final void selectIn(SelectInContext context, final boolean requestFocus) {
    final Object selector = context.getSelectorInFile();
    if (selector instanceof PsiElement) {
      select((PsiElement)selector, requestFocus);
    } else {
      select(selector, context.getVirtualFile(), requestFocus);
    }
  }

  protected abstract void select(final Object selector, VirtualFile virtualFile, final boolean requestFocus);

  protected abstract boolean canWorkWithCustomObjects();

  public abstract String getToolWindowId();

  public abstract String getMinorViewId();

  protected abstract void select(PsiElement element, boolean requestFocus);
}
