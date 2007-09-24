package com.intellij.ide.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public abstract class ProjectViewSelectInTarget extends SelectInTargetPsiWrapper {
  private String mySubId;

  protected ProjectViewSelectInTarget(Project project) {
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
      windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW).activate(runnable, false);
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

  public void select(PsiElement element, final boolean requestFocus) {
    PsiElement current = element;
    while (current != null) {
      if (current instanceof PsiFileSystemItem) {
        break;
      }
      if (isTopLevelClass(current)) {
        break;
      }
      current = current.getParent();
    }

    if (current instanceof PsiJavaFile) {
      PsiClass[] classes = ((PsiJavaFile)current).getClasses();
      if (classes.length > 0 && isTopLevelClass(classes[0])) {
        current = classes[0];
      }
    }
    if (current == null) {
      current = element;
    }
    PsiElement originalElement = current.getOriginalElement();
    JspFile jspFile = PsiUtil.getJspFile(originalElement);
    final VirtualFile virtualFile = PsiUtil.getVirtualFile(originalElement);
    select(jspFile == null ? originalElement : jspFile, virtualFile, requestFocus);
  }

  private static boolean isTopLevelClass(final PsiElement element) {
    if (!(element instanceof PsiClass)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    
    return parent instanceof PsiFile && !PsiUtil.isInJspFile(element);
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
