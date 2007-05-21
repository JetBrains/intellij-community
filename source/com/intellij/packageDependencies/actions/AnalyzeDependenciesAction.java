package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AnalyzeDependenciesAction extends BaseAnalysisAction {
  public AnalyzeDependenciesAction() {
    super(AnalysisScopeBundle.message("action.forward.dependency.analysis"), AnalysisScopeBundle.message("action.analysis.noun"));
  }

  protected void analyze(@NotNull final Project project, AnalysisScope scope) {
    new AnalyzeDependenciesHandler(project, scope).analyze();
  }
}
