package com.intellij.cyclicDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.cyclicDependencies.CyclicDependenciesBuilder;
import com.intellij.cyclicDependencies.ui.CyclicDependenciesPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CyclicDependenciesHandler {
  private Project myProject;
  private AnalysisScope myScope;

  public CyclicDependenciesHandler(Project project, AnalysisScope scope) {
    myProject = project;
    myScope = scope;
  }

  public void analyze() {
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject, myScope);
    if (ApplicationManager.getApplication().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        builder.analyze();
      }
    }, "Analyzing Dependencies", true, myProject)) {
      CyclicDependenciesPanel panel = new CyclicDependenciesPanel(myProject, builder);
      Content content = PeerFactory.getInstance().getContentFactory().createContent(panel,
                                                                                  "Cyclic Dependencies of " + builder.getScope().getDisplayName(),
                                                                                  false);
      panel.setContent(content);
      ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).addContent(content);
    }
  }
}
