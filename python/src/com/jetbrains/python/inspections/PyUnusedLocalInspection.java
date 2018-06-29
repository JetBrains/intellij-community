/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private static final Key<PyUnusedLocalInspectionVisitor> KEY = Key.create("PyUnusedLocal.Visitor");

  public boolean ignoreTupleUnpacking = true;
  public boolean ignoreLambdaParameters = true;
  public boolean ignoreLoopIterationVariables = true;
  public boolean ignoreVariablesStartingWithUnderscore = true;

  @Override
  @NotNull
  @Nls
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unused");
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    final PyUnusedLocalInspectionVisitor visitor = new PyUnusedLocalInspectionVisitor(holder,
                                                                                      session,
                                                                                      ignoreTupleUnpacking,
                                                                                      ignoreLambdaParameters,
                                                                                      ignoreLoopIterationVariables,
                                                                                      ignoreVariablesStartingWithUnderscore);
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
      visitor.registerProblems();
      session.putUserData(KEY, null);
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Ignore variables used in tuple unpacking", "ignoreTupleUnpacking");
    panel.addCheckbox("Ignore lambda parameters", "ignoreLambdaParameters");
    panel.addCheckbox("Ignore range iteration variables", "ignoreLoopIterationVariables");
    panel.addCheckbox("Ignore variables starting with '_'", "ignoreVariablesStartingWithUnderscore");
    return panel;
  }
}
