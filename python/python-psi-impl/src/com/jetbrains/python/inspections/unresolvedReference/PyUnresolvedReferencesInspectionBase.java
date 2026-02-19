package com.jetbrains.python.inspections.unresolvedReference;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.inspections.PyInspection;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

public abstract class PyUnresolvedReferencesInspectionBase extends PyInspection {
  private static final Key<PyUnresolvedReferencesVisitor> KEY = Key.create("PyUnresolvedReferencesInspection.Visitor");

  @Pattern(VALID_ID_PATTERN)
  @Override
  public @NotNull String getID() {
    return "PyUnresolvedReferences";
  }

  @Override
  public final @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                       final boolean isOnTheFly,
                                                       final @NotNull LocalInspectionToolSession session) {
    final PyUnresolvedReferencesVisitor visitor = createVisitor(holder, session);
    // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
    final PyUnresolvedReferencesVisitor existingVisitor = session.getUserData(KEY);
    if (existingVisitor == null) {
      session.putUserData(KEY, visitor);
    }
    return visitor;
  }

  @Override
  public final void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder holder) {
    final PyUnresolvedReferencesVisitor visitor = session.getUserData(KEY);
    assert visitor != null;
    ReadAction.run(() -> {
      visitor.addInstallAllImports();
    });
    session.putUserData(KEY, null);
  }

  protected abstract PyUnresolvedReferencesVisitor createVisitor(@NotNull ProblemsHolder holder,
                                                                 @NotNull LocalInspectionToolSession session);
}
