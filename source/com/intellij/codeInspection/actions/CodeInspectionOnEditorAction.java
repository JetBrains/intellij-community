package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
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
      analyze(project, psiFile);
    }
  }

  protected void analyze(Project project, PsiFile psiFile) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final InspectionManagerEx inspectionManagerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final AnalysisScope scope = new AnalysisScope(psiFile);
    inspectionManagerEx.setCurrentScope(scope);
    final InspectionProfileImpl inspectionProfile = DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile(psiFile);
    inspectionManagerEx.setExternalProfile(inspectionProfile);
    inspectionManagerEx.doInspections(scope);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        inspectionManagerEx.setExternalProfile(null);
      }
    });
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
    e.getPresentation().setEnabled(project != null && psiFile != null);
  }
}
