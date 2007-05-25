package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.copy.CopyHandler;

public class CloneElementAction extends CopyElementAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CloneElementAction");

  protected void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    LOG.assertTrue(elements.length == 1);
    CopyHandler.doClone(elements[0]);
  }

  protected void updateForEditor(DataContext dataContext, Presentation presentation) {
    super.updateForEditor(dataContext, presentation);
    presentation.setVisible(false);
  }

  protected void updateForToolWindow(String id, DataContext dataContext,Presentation presentation) {
    // work only with single selection
    PsiElement[] elements = (PsiElement[])dataContext.getData(DataConstantsEx.PSI_ELEMENT_ARRAY);
    presentation.setEnabled(elements != null && elements.length == 1 && CopyHandler.canCopy(elements));
    presentation.setVisible(true);

    if (!ToolWindowId.COMMANDER.equals(id)) {
      presentation.setVisible(false);
    }
  }
}
