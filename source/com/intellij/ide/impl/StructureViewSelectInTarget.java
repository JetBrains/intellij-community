package com.intellij.ide.impl;

import com.intellij.ide.SelectInTarget;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;

public class StructureViewSelectInTarget implements SelectInTarget {
  private Project myProject;

  public StructureViewSelectInTarget(Project project) {
    myProject = project;
  }


  public String toString() {
    return "File Structure";
  }

  /**
   * This is called in an atomic action
   */
  public boolean canSelect(PsiFile file) {
    StructureView structureView = getStructureView();
    if (file == null || structureView == null || !FileEditorManager.getInstance(myProject).getSelectedEditor(file.getVirtualFile()).equals(structureView.getFileEditor())) {
      return false;
    }
    return (file instanceof PsiJavaFile || file instanceof XmlFile) && file.isValid();
  }

  public void select(PsiElement element, final boolean requestFocus) {
    PsiElement targetElement = element;
    while (true) {
      if (targetElement instanceof PsiClass && !(targetElement instanceof PsiAnonymousClass)) break;
      if (targetElement instanceof PsiMethod) {
        if (!(targetElement.getParent() instanceof PsiAnonymousClass)) break;
      }
      if (targetElement == null || targetElement instanceof PsiField || targetElement instanceof PsiFile) break;
      targetElement = targetElement.getParent();
    }
    if (targetElement == null) return;
    final PsiElement theElement = targetElement;

    final StructureView structureView = getStructureView();
    ToolWindowManager windowManager=ToolWindowManager.getInstance(element.getProject());
    final Runnable runnable = new Runnable() {
      public void run() {
        VirtualFile virtualFile = theElement.getContainingFile().getVirtualFile();
        FileEditor editor = FileEditorManager.getInstance(myProject).getSelectedEditor(virtualFile);
        structureView.select(theElement, editor,requestFocus);
      }
    };
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.STRUCTURE_VIEW).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  private StructureView getStructureView() {
    return StructureViewFactory.getInstance(myProject).getStructureView();
  }

  public String getToolWindowId() {
    return ToolWindowId.STRUCTURE_VIEW;
  }

  public String getMinorViewId() {
    return null;
  }
}
