/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.PyRenameElementQuickFix;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Warns about shadowing built-in names.
 *
 * @author vlan
 */
public class PyShadowingBuiltinsInspection extends PyPsiShadowingBuiltinsInspection {

  @NotNull
  @Override
  protected LocalQuickFix[] createQuickFixes(String name, PsiElement problemElement) {
    return new LocalQuickFix[]{new PyRenameElementQuickFix(problemElement), new PyIgnoreBuiltinQuickFix(name)};
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListEditForm form = new ListEditForm("Ignore built-ins", ignoredNames);
    return form.getContentPanel();
  }

  private static class PyIgnoreBuiltinQuickFix implements LocalQuickFix, LowPriorityAction {
    @NotNull private final String myName;

    private PyIgnoreBuiltinQuickFix(@NotNull String name) {
      myName = name;
    }

    @NotNull
    @Override
    public String getName() {
      return getFamilyName() + " \"" + myName + "\"";
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return PyBundle.message("INSP.shadowing.builtins.ignore.shadowed.built.in.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
        InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, it -> {
          final String toolName = PyShadowingBuiltinsInspection.class.getSimpleName();
          final PyShadowingBuiltinsInspection inspection = (PyShadowingBuiltinsInspection)it.getUnwrappedTool(toolName, element);
          if (inspection != null) {
            if (!inspection.ignoredNames.contains(myName)) {
              inspection.ignoredNames.add(myName);
            }
          }
        });
      }
    }
  }
}

