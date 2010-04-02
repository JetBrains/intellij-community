package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class PyUnusedLocalVariableInspection extends LocalInspectionTool {
  private final ThreadLocal<PyUnusedLocalVariableInspectionVisitor> myLastVisitor = new ThreadLocal<PyUnusedLocalVariableInspectionVisitor>();

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @NotNull
  @Nls
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unused");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "PyUnusedLocalVariableInspection";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    final PyUnusedLocalVariableInspectionVisitor visitor = new PyUnusedLocalVariableInspectionVisitor(holder);
    myLastVisitor.set(visitor);
    return visitor;
  }

  @Override
  public void inspectionFinished(LocalInspectionToolSession session) {
    final PyUnusedLocalVariableInspectionVisitor visitor = myLastVisitor.get();
    assert visitor != null;
    visitor.registerProblems();
    myLastVisitor.remove();
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

}
