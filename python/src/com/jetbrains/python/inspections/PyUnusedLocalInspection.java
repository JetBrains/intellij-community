package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author oleg
 */
public class PyUnusedLocalInspection extends PyInspection {
  private static Key<PyUnusedLocalInspectionVisitor> KEY = Key.create("PyUnusedLocal.Visitor");

  public boolean ignoreTupleUnpacking = true;
  public boolean ignoreLambdaParameters = true;
  public boolean ignoreLoopIterationVariables = true;

  @NotNull
  @Nls
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unused");
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    final PyUnusedLocalInspectionVisitor visitor = new PyUnusedLocalInspectionVisitor(holder, ignoreTupleUnpacking, ignoreLambdaParameters,
                                                                                      ignoreLoopIterationVariables);
    // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
    final PyUnusedLocalInspectionVisitor existingVisitor = session.getUserData(KEY);
    if (existingVisitor == null) {
      session.putUserData(KEY, visitor);
    }
    return visitor;
  }

  @Override
  public void inspectionFinished(LocalInspectionToolSession session) {
    final PyUnusedLocalInspectionVisitor visitor = session.getUserData(KEY);
    if (visitor != null) {
      visitor.registerProblems();
      session.putUserData(KEY, null);
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Ignore variables used in tuple unpacking", "ignoreTupleUnpacking");
    panel.addCheckbox("Ignore lambda parameters", "ignoreLambdaParameters");
    panel.addCheckbox("Ignore range iteration variables", "ignoreLoopIterationVariables");
    return panel;
  }
}
