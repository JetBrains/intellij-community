package com.intellij.ide.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public abstract class ProjectViewSelectInTarget extends SelectInTargetPsiWrapper {
  private String mySubId;

  public ProjectViewSelectInTarget(Project project) {
    super(project);
  }

  protected final void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    final ProjectView projectView = ProjectView.getInstance(myProject);
    ToolWindowManager windowManager=ToolWindowManager.getInstance(myProject);
    final Runnable runnable = new Runnable() {
      public void run() {
        if (requestFocus) {
          projectView.changeView(getMinorViewId(), mySubId);
        }
        projectView.select(selector, virtualFile, requestFocus);
      }
    };
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  @NotNull
  public String[] getSubIds() {
    AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getProjectViewPaneById(getMinorViewId());
    return pane.getSubIds();
  }
  public boolean isSubIdSelectable(String subId, VirtualFile file) {
    return false;
  }
  public String getSubIdPresentableName(String subId) {
    AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getProjectViewPaneById(getMinorViewId());
    return pane.getPresentableSubIdName(subId);
  }

  public final void select(PsiElement element, final boolean requestFocus) {
    while (true) {
      if (element instanceof PsiFile) {
        break;
      }
      if (isTopLevelClass(element)) {
        break;
      }
      element = element.getParent();
    }

    if (element instanceof PsiJavaFile) {
      PsiClass[] classes = ((PsiJavaFile)element).getClasses();
      if (classes.length > 0 && isTopLevelClass(classes[0])) {
        element = classes[0];
      }
    }
    final PsiElement originalElement = element.getOriginalElement();
    final VirtualFile virtualFile = PsiUtil.getVirtualFile(originalElement);
    select(originalElement, virtualFile,requestFocus);
  }

  private static boolean isTopLevelClass(final PsiElement element) {
    if (!(element instanceof PsiClass)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiFile)) {
      return false;
    }
    final VirtualFile virtualFile = ((PsiFile)parent).getVirtualFile();
    if (virtualFile == null) {
      return false;
    }
    return virtualFile.getFileType() == StdFileTypes.JAVA || virtualFile.getFileType() == StdFileTypes.CLASS;
  }

  public final String getToolWindowId() {
    return ToolWindowId.PROJECT_VIEW;
  }

  protected boolean canWorkWithCustomObjects() {
    return true;
  }
  public final void setSubId(String subId) {
    mySubId = subId;
  }
}
