package com.intellij.ide.impl;

import com.intellij.aspects.psi.PsiAspect;
import com.intellij.aspects.psi.PsiAspectFile;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

public abstract class ProjectViewSelectInTarget implements SelectInTarget {
  protected Project myProject;

  public ProjectViewSelectInTarget(Project project) {
    myProject = project;
  }

  public void select(PsiElement element, final boolean requestFocus) {
    while (true) {
      if (element instanceof PsiFile) break;
      if (element instanceof PsiClass && element.getParent() instanceof PsiFile) break;
      element = element.getParent();
    }
    if (element instanceof PsiAspectFile) {
      PsiAspect[] aspects = ((PsiAspectFile) element).getAspects();
      if (aspects.length > 0) {
        element = aspects[0];
      }
    }
    else if (element instanceof PsiJavaFile) {
      PsiClass[] classes = ((PsiJavaFile)element).getClasses();
      if (classes.length > 0) {
        element = classes[0];
      }
    }
    final ProjectView projectView = ProjectView.getInstance(myProject);
    final PsiElement _element1 = element.getOriginalElement();
    ToolWindowManager windowManager=ToolWindowManager.getInstance(myProject);
    final Runnable runnable = new Runnable() {
      public void run() {
        if (requestFocus) {
          projectView.changeView(getMinorViewId());
        }
        projectView.selectPsiElement(_element1, requestFocus);
      }
    };
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  public String getToolWindowId() {
    return ToolWindowId.PROJECT_VIEW;
  }
}
