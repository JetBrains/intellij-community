package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public class CodeInspectionOnEditorAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null){
      return;
    }
    PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
    if (psiFile != null){
      analyze(project, new AnalysisScope(psiFile, AnalysisScope.SOURCE_JAVA_FILES));
    }
  }

  protected void analyze(Project project, AnalysisScope scope) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final InspectionManagerEx inspectionManagerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    inspectionManagerEx.setCurrentScope(scope);
    final InspectionProfileImpl inspectionProfile = DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile();
    //side effect to init non local tools
    inspectionProfile.getInspectionTools(project);
    inspectionManagerEx.setExternalProfile(inspectionProfile);
    inspectionManagerEx.doInspections(scope, false);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        inspectionManagerEx.setExternalProfile(null);
      }
    });
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(e.getPlace().equals(ActionPlaces.EDITOR_POPUP));
  }
}
