package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;

public class AnalyzeDependenciesHandler {
  private Project myProject;
  private AnalysisScope myScope;

  public AnalyzeDependenciesHandler(Project project, AnalysisScope scope) {
    myProject = project;
    myScope = scope;
  }

  public void analyze() {
    final DependenciesBuilder forwardBuilder = new ForwardDependenciesBuilder(myProject, myScope);
    if (ApplicationManager.getApplication().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        forwardBuilder.analyze();
      }
    }, "Analyzing Dependencies", true, myProject)) {
      DependenciesPanel panel = new DependenciesPanel(myProject, forwardBuilder);
      Content content = PeerFactory.getInstance().getContentFactory().createContent(panel,
                                                                                  "Dependencies of " + forwardBuilder.getScope().getDisplayName(),
                                                                                  false);
      panel.setContent(content);
      ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).addContent(content);
    }
  }
}