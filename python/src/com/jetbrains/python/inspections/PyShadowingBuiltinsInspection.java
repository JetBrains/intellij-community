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

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.inspections.quickfix.PyRenameElementQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Warns about shadowing built-in names.
 *
 * @author vlan
 */
public class PyShadowingBuiltinsInspection extends PyInspection {
  // Persistent settings
  public List<String> ignoredNames = new ArrayList<>();

  @NotNull
  @Override
  public String getDisplayName() {
    return "Shadowing built-ins";
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListEditForm form = new ListEditForm("Ignore built-ins", ignoredNames);
    return form.getContentPanel();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session, ignoredNames);
  }

  private static class Visitor extends PyInspectionVisitor {
    private final Set<String> myIgnoredNames;

    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session, @NotNull Collection<String> ignoredNames) {
      super(holder, session);
      myIgnoredNames = ImmutableSet.copyOf(ignoredNames);
    }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
      processElement(node);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      processElement(node);
    }

    @Override
    public void visitPyNamedParameter(@NotNull PyNamedParameter node) {
      processElement(node);
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      if (!node.isQualified()) {
        processElement(node);
      }
    }

    private void processElement(@NotNull PsiNameIdentifierOwner element) {
      final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
      if (owner instanceof PyClass) {
        return;
      }
      final String name = element.getName();
      if (name != null && !myIgnoredNames.contains(name)) {
        final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
        final PsiElement builtin = builtinCache.getByName(name);
        if (builtin != null && !PyUtil.inSameFile(builtin, element)) {
          final PsiElement identifier = element.getNameIdentifier();
          final PsiElement problemElement = identifier != null ? identifier : element;
          registerProblem(problemElement, String.format("Shadows built-in name '%s'", name),
                          ProblemHighlightType.WEAK_WARNING, null, new PyRenameElementQuickFix(), new PyIgnoreBuiltinQuickFix(name));
        }
      }
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

      @NotNull
      @Override
      public String getFamilyName() {
        return "Ignore shadowed built-in name";
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (element != null) {
          final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
          profile.modifyProfile(model -> {
            final String toolName = PyShadowingBuiltinsInspection.class.getSimpleName();
            final PyShadowingBuiltinsInspection inspection = (PyShadowingBuiltinsInspection)model.getUnwrappedTool(toolName, element);
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
}

