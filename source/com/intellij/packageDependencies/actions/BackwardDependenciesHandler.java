package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.BackwardDependenciesBuilder;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;

import javax.swing.*;

/**
 * User: anna
 * Date: Jan 16, 2005
 */
public class BackwardDependenciesHandler {
  private Project myProject;
  private AnalysisScope myScope;
  private final AnalysisScope myScopeOfInterest;

  public BackwardDependenciesHandler(Project project, AnalysisScope scope, final AnalysisScope selectedScope) {
    myProject = project;
    myScope = scope;
    myScopeOfInterest = selectedScope;
  }

  public void analyze() {
    final DependenciesBuilder builder = new BackwardDependenciesBuilder(myProject, myScope, myScopeOfInterest);
    final Runnable process = new Runnable() {
      public void run() {
        builder.analyze();
      }
    };
    final Runnable successRunnable = new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            DependenciesPanel panel = new DependenciesPanel(myProject, builder);
            Content content = PeerFactory.getInstance().getContentFactory().createContent(panel, AnalysisScopeBundle.message(
              "backward.dependencies.toolwindow.title", builder.getScope().getDisplayName()), false);
            content.setDisposer(panel);
            panel.setContent(content);
            ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).addContent(content);
          }
        });
      }
    };

    ProgressManager.getInstance()
      .runProcessWithProgressAsynchronously(myProject, AnalysisScopeBundle.message("backward.dependencies.progress.text"),
                                            process, successRunnable, null, new PerformAnalysisInBackgroundOption(myProject));

  }
}
