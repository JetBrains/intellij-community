package com.intellij.ide.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
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
    PsiElement toSelect = getElementToSelect(element);
    if (toSelect == null) return;
    PsiElement originalElement = toSelect.getOriginalElement();
    final VirtualFile virtualFile = PsiUtil.getVirtualFile(originalElement);
    select(originalElement, virtualFile, requestFocus);
  }

  private static PsiElement getElementToSelect(PsiElement element) {
    PsiFile baseRootFile = getBaseRootFile(element);
    if (baseRootFile == null) return null;
    PsiElement current = element;
    while (current != null) {
      if (current instanceof PsiFileSystemItem) {
        break;
      }
      if (isTopLevelClass(current, baseRootFile)) {
        break;
      }
      current = current.getParent();
    }

    if (current instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)current).getClasses();
      if (classes.length > 0 && isTopLevelClass(classes[0], baseRootFile)) {
        current = classes[0];
      }
    }
    return current instanceof PsiClass ? current : baseRootFile;
  }

  private static PsiFile getBaseRootFile(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;

    final FileViewProvider viewProvider = containingFile.getViewProvider();
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  private static boolean isTopLevelClass(final PsiElement element, PsiFile baseRootFile) {
    if (!(element instanceof PsiClass)) {
      return false;
    }
    final PsiElement parent = element.getParent();
                                        // do not select JspClass
    return parent instanceof PsiFile && parent.getLanguage() == baseRootFile.getLanguage();
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
