package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.BackwardDependenciesBuilder;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;

/**
 * User: anna
 * Date: Jan 16, 2005
 */
public class BackwardDependenciesHandler {
  private Project myProject;
  private AnalysisScope myScope;

  public BackwardDependenciesHandler(Project project, AnalysisScope scope) {
    myProject = project;
    myScope = scope;
  }

  public void analyze() {
    final DependenciesBuilder builder = new BackwardDependenciesBuilder(myProject, myScope);

    if (ApplicationManager.getApplication().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        builder.analyze();
      }
    }, "Analyzing Backward Dependencies", true, myProject)) {
      DependenciesPanel panel = new DependenciesPanel(myProject, builder);
      Content content = PeerFactory.getInstance().getContentFactory().createContent(panel,
                                                                                  "Backward Dependencies of " + builder.getScope().getDisplayName(),
                                                                                  false);
      panel.setContent(content);
      ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).addContent(content);

    }
  }
}
