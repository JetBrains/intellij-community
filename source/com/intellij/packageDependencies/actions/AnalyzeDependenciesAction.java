package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AnalyzeDependenciesAction extends BaseAnalysisAction {
  private JCheckBox myTransitiveCB;
  private JPanel myWholePanel;

  public AnalyzeDependenciesAction() {
    super(AnalysisScopeBundle.message("action.forward.dependency.analysis"), AnalysisScopeBundle.message("action.analysis.noun"));
  }

  protected void analyze(@NotNull final Project project, AnalysisScope scope) {
    new AnalyzeDependenciesHandler(project, scope, myTransitiveCB.isSelected()).analyze();
  }

  @Nullable
  protected JComponent getAdditionalActionSettings(final Project project, final BaseAnalysisActionDialog dialog) {
    myTransitiveCB.setText("Show transitive dependencies");
    return myWholePanel;
  }
}
