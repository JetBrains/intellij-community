package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.GotoSymbolModel;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;

/**
 * Author: msk
 */
public abstract class GotoActionBase extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.GotoActionBase");

  protected static Class myInAction = null;

  public final void actionPerformed(AnActionEvent e) {
    LOG.assertTrue (!getClass ().equals (myInAction));
    try {
      myInAction = getClass();
      gotoActionPerformed (e);
    }
    catch (Throwable t) {
      myInAction = null;
    }
  }

  protected abstract void gotoActionPerformed(AnActionEvent e);

  public final void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    presentation.setEnabled(!getClass ().equals (myInAction) && project != null);
  }


  //protected abstract void navigateToFile(Project project, PsiElement element);
}
