package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;

public class CodeInspectionAction extends BaseAnalysisAction {
  public CodeInspectionAction() {
    super(AnalysisScope.SOURCE_JAVA_FILES, "Inspection", "Inspect", "Inspection");
  }

  protected void analyze(Project project, AnalysisScope scope) {
    FileDocumentManager.getInstance().saveAllDocuments();
    ((InspectionManagerEx) InspectionManager.getInstance(project)).doInspections(scope, true);
  }
}
