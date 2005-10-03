package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Jan 16, 2005
 */
public class BackwardDependenciesAction extends BaseAnalysisAction{
  public BackwardDependenciesAction() {
    super(AnalysisScopeBundle.message("action.backward.dependency.analysis"), AnalysisScopeBundle.message("action.analysis.noun"));
  }

  protected void analyze(Project project, AnalysisScope scope) {
    new BackwardDependenciesHandler(project, scope).analyze();
  }
}
