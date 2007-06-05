package com.intellij.ide.commander;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.SelectInTargetPsiWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

public final class CommanderSelectInTarget extends SelectInTargetPsiWrapper {
  public CommanderSelectInTarget(final Project project) {
    super(project);
  }

  public String toString() {
    return SelectInManager.COMMANDER;
  }

  protected boolean canSelect(final PsiFileSystemItem file) {
    return file.getManager().isInProject(file);
  }

  protected void select(PsiElement element, boolean requestFocus) {
    while (true) {
      if (element instanceof PsiFile) {
        break;
      }
      if (element instanceof PsiClass && element.getParent() instanceof PsiFile) {
        break;
      }
      element = element.getParent();
    }

    if (element instanceof PsiJavaFile) {
      final PsiClass[] classes = ((PsiJavaFile)element).getClasses();
      if (classes.length > 0) {
        element = classes[0];
      }
    }

    final PsiElement _element = element.getOriginalElement();

    selectElementInCommander(new Runnable() {
      public void run() {
        Commander.getInstance(myProject).selectElementInLeftPanel(_element, PsiUtil.getVirtualFile(_element));
      }
    }, requestFocus);
  }

  private void selectElementInCommander(final Runnable runnable, final boolean requestFocus) {
    final ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.COMMANDER).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  protected void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    selectElementInCommander(new Runnable() {
      public void run() {
        final Commander commander = Commander.getInstance(myProject);
        commander.selectElementInLeftPanel(selector, virtualFile);
      }
    }, requestFocus);

  }

  protected boolean canWorkWithCustomObjects() {
    return false;
  }

  public String getToolWindowId() {
    return ToolWindowId.COMMANDER;
  }

  public String getMinorViewId() {
    return null;
  }

  public float getWeight() {
    return StandardTargetWeights.COMMANDER_WEIGHT;
  }

}
