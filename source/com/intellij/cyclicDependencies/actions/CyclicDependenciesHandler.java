package com.intellij.cyclicDependencies.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.analysis.AnalysisScope;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.ui.content.Content;
import com.intellij.peer.PeerFactory;
import com.intellij.cyclicDependencies.CyclicDependenciesBuilder;
import com.intellij.cyclicDependencies.ui.CyclicDependenciesPanel;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CyclicDependenciesHandler {
  private Project myProject;
  private AnalysisScope myScope;
  private int myPerPackageCycleCount;

  public CyclicDependenciesHandler(Project project, AnalysisScope scope, int perPackageCycleCount) {
    myProject = project;
    myScope = scope;
    myPerPackageCycleCount = perPackageCycleCount;
  }

  public void analyze() {
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject, myScope, myPerPackageCycleCount);
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
      DependencyValidationManager.getInstance(myProject).addContent(content);
    }
  }
}
