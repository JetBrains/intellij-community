package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

public class GotoClassAction extends GotoActionBase {
  public void gotoActionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.class");
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, new GotoClassModel2(project));

    popup.invoke(new ChooseByNameBase.Callback() {
      public void onClose () {
        if (GotoClassAction.class.equals (myInAction))
          myInAction = null;
      }
      public void elementChosen(Object element) {
        ((NavigationItem)element).navigate(true);
      }
    }, ModalityState.current(), true);
  }

}