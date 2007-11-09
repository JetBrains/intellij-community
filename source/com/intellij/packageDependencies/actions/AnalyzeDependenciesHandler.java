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
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;

import javax.swing.*;
import java.util.*;

public class AnalyzeDependenciesHandler {
  private final Project myProject;
  private final List<AnalysisScope> myScopes;
  private final boolean myTransitive;
  private final Set<PsiFile> myExcluded;

  public AnalyzeDependenciesHandler(Project project, List<AnalysisScope> scopes,boolean isTransitive, Set<PsiFile> excluded) {
    myProject = project;
    myScopes = scopes;
    myTransitive = isTransitive;
    myExcluded = excluded;
  }

  public AnalyzeDependenciesHandler(final Project project, final AnalysisScope scope, final boolean isTransitive) {
    this(project, Collections.singletonList(scope), isTransitive, new HashSet<PsiFile>());
  }

  public void analyze() {
    final List<DependenciesBuilder> builders = new ArrayList<DependenciesBuilder>();
    for (AnalysisScope scope : myScopes) {
      builders.add(new ForwardDependenciesBuilder(myProject, scope, myTransitive));
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
            DependenciesPanel panel = new DependenciesPanel(myProject, builders, myExcluded);
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