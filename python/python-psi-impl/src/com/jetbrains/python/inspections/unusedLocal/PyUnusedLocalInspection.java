// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unusedLocal;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class PyUnusedLocalInspection extends PyInspection {
  private static final Key<PyUnusedLocalInspectionVisitor> KEY = Key.create("PyUnusedLocal.Visitor");

  public boolean ignoreTupleUnpacking = true;
  public boolean ignoreLambdaParameters = true;
  public boolean ignoreLoopIterationVariables = true;
  public boolean ignoreVariablesStartingWithUnderscore = true;

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    final PyUnusedLocalInspectionVisitor visitor = new PyUnusedLocalInspectionVisitor(holder,
                                                                                      ignoreTupleUnpacking,
                                                                                      ignoreLambdaParameters,
                                                                                      ignoreLoopIterationVariables,
                                                                                      ignoreVariablesStartingWithUnderscore, PyInspectionVisitor.getContext(session));
    // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
    final PyUnusedLocalInspectionVisitor existingVisitor = session.getUserData(KEY);
    if (existingVisitor == null) {
      session.putUserData(KEY, visitor);
    }
    return visitor;
  }

  @Override
  public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder holder) {
    final PyUnusedLocalInspectionVisitor visitor = session.getUserData(KEY);
    if (visitor != null) {
      ReadAction.run(() -> visitor.registerProblems());
      session.putUserData(KEY, null);
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreTupleUnpacking", PyPsiBundle.message("INSP.unused.locals.ignore.variables.used.in.tuple.unpacking")),
      checkbox("ignoreLambdaParameters", PyPsiBundle.message("INSP.unused.locals.ignore.lambda.parameters")),
      checkbox("ignoreLoopIterationVariables", PyPsiBundle.message("INSP.unused.locals.ignore.range.iteration.variables")),
      checkbox("ignoreVariablesStartingWithUnderscore", PyPsiBundle.message("INSP.unused.locals.ignore.variables.starting.with"))
    );
  }
}
