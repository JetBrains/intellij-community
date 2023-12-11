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
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * Warns about shadowing built-in names.
 *
 */
public final class PyShadowingBuiltinsInspection extends PyInspection {

  // Persistent settings
  public List<String> ignoredNames = new ArrayList<>();

  protected @NotNull LocalQuickFix @NotNull [] createQuickFixes(String name, PsiElement problemElement) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    LocalQuickFix qf = PythonUiService.getInstance().createPyRenameElementQuickFix(problemElement);
    if (qf != null) {
      fixes.add(qf);
    }
    fixes.add(new PyIgnoreBuiltinQuickFix(name));
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(OptPane.stringList("ignoredNames", PyPsiBundle.message("INSP.shadowing.builtins.ignore.built.ins.label")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session), ignoredNames);
  }

  private static final class PyIgnoreBuiltinQuickFix implements LocalQuickFix, LowPriorityAction {
    @NotNull private final @NlsSafe String myName;

    private PyIgnoreBuiltinQuickFix(@NotNull String name) {
      myName = name;
    }

    @NotNull
    @Override
    public String getName() {
      return PyPsiBundle.message("QFIX.ignore.shadowed.built.in.name", myName);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return PyPsiBundle.message("QFIX.NAME.ignore.shadowed.built.in.name");
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

  private class Visitor extends PyInspectionVisitor {
    private final Set<String> myIgnoredNames;

    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context, @NotNull Collection<String> ignoredNames) {
      super(holder, context);
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
          registerProblem(problemElement, PyPsiBundle.message("INSP.shadowing.builtins.shadows.built.in.name", name),
                          ProblemHighlightType.WEAK_WARNING, null, createQuickFixes(name, problemElement));
        }
      }
    }
  }
}

