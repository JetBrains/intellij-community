
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * @author Eugene Belyaev
 */
public class GotoFileAction extends GotoActionBase {
  public void gotoActionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.file");
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, new GotoFileModel(project));
    popup.invoke(new ChooseByNameBase.Callback() {
      public void onClose () {
        if (GotoFileAction.class.equals (myInAction))
          myInAction = null;
      }
      public void elementChosen(Object element){
        final PsiFile file = (PsiFile)element;
        if (file != null){
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              OpenFileDescriptor descriptor=new OpenFileDescriptor(project, file.getVirtualFile());
              FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
            }
          }, ModalityState.NON_MMODAL);
        }
      }
    }, ModalityState.current(), true);
  }
}