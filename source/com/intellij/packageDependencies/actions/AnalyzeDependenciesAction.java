package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.openapi.project.Project;

public class AnalyzeDependenciesAction extends BaseAnalysisAction {
  public AnalyzeDependenciesAction() {
    super(AnalysisScope.SOURCE_JAVA_FILES, "Dependency Analysis", "Analyze", "Analysis");
  }

  protected void analyze(final Project project, AnalysisScope scope) {
    new AnalyzeDependenciesHandler(project, scope).analyze();
  }
}
