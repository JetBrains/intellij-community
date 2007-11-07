package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnalyzeDependenciesHandler {
  private final Project myProject;
  private final List<AnalysisScope> myScopes;

  public AnalyzeDependenciesHandler(Project project, AnalysisScope scope) {
    this(project, Collections.singletonList(scope));
  }

  public AnalyzeDependenciesHandler(Project project, List<AnalysisScope> scopes) {
    myProject = project;
    myScopes = scopes;
  }

  public void analyze() {
    final List<DependenciesBuilder> builders = new ArrayList<DependenciesBuilder>();
    for (AnalysisScope scope : myScopes) {
      builders.add(new ForwardDependenciesBuilder(myProject, scope));
    }
    final Runnable process = new Runnable() {
      public void run() {
        for (final DependenciesBuilder builder : builders) {
          builder.analyze();
        }
      }
    };
    final Runnable successRunnable = new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            DependenciesPanel panel = new DependenciesPanel(myProject, builders);
            Content content = PeerFactory.getInstance().getContentFactory().createContent(panel, AnalysisScopeBundle.message(
              "package.dependencies.toolwindow.title", builders.get(0).getScope().getDisplayName()), false);
            content.setDisposer(panel);
            panel.setContent(content);
            ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).addContent(content);
          }
        });
      }
    };
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(myProject,
                                                                       AnalysisScopeBundle.message("package.dependencies.progress.title"),
                                                                       process, successRunnable, null,
                                                                       new PerformAnalysisInBackgroundOption(myProject));
  }
}