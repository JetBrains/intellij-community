package com.intellij.packageDependencies.actions;

import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Jan 16, 2005
 */
public class BackwardDependenciesAction extends BaseAnalysisAction{
  public BackwardDependenciesAction() {
    super(AnalysisScope.SOURCE_JAVA_FILES, "Backward Dependency Analysis", "Analyze", "Analysis");
  }

  protected void analyze(Project project, AnalysisScope scope) {
    new BackwardDependenciesHandler(project, scope).analyze();
  }
}
