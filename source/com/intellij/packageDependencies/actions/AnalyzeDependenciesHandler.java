package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyValidationManager;

public class AnalyzeDependenciesHandler {
  private Project myProject;
  private AnalysisScope myScope;

  public AnalyzeDependenciesHandler(Project project, AnalysisScope scope) {
    myProject = project;
    myScope = scope;
  }

  public void analyze() {
    final DependenciesBuilder builder = new DependenciesBuilder(myProject, myScope);
    if (ApplicationManager.getApplication().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        builder.analyze();
      }
    }, "Analyzing Dependencies", true, myProject)) {
      DependencyValidationManager.getInstance(myProject).addContent(builder);
    }
  }
}