/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.inspections.quickfix.PyRenameElementQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Warns about shadowing names defined in outer scopes.
 *
 * @author vlan
 */
public class PyShadowingNamesInspection extends PyInspection {
  @NotNull
  @Override
  public String getDisplayName() {
    return "Shadowing names from outer scopes";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
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
      if (node.isSelf()) {
        return;
      }
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
      if (name != null) {
        final PsiElement identifier = element.getNameIdentifier();
        final PsiElement problemElement = identifier != null ? identifier : element;
        if (PyNames.UNDERSCORE.equals(name) || name.startsWith(PyNames.UNDERSCORE) && element instanceof PyParameter) {
          return;
        }
        if (owner != null) {
          final ScopeOwner nextOwner = ScopeUtil.getScopeOwner(owner);
          if (nextOwner != null) {
            final PyResolveProcessor processor = new PyResolveProcessor(name);
            PyResolveUtil.scopeCrawlUp(processor, nextOwner, null, name, null);
            for (PsiElement resolved : processor.getElements()) {
              if (resolved != null) {
                final PyComprehensionElement comprehension = PsiTreeUtil.getParentOfType(resolved, PyComprehensionElement.class);
                if (comprehension != null && PyUtil.isOwnScopeComprehension(comprehension)) {
                  return;
                }
                final Scope scope = ControlFlowCache.getScope(owner);
                if (scope.isGlobal(name) || scope.isNonlocal(name)) {
                  return;
                }
                if (Arrays.stream(PyInspectionExtension.EP_NAME.getExtensions())
                          .anyMatch(o -> o.ignoreShadowed(resolved))) {
                  return;
                }
                registerProblem(problemElement, String.format("Shadows name '%s' from outer scope", name),
                                ProblemHighlightType.WEAK_WARNING, null, new PyRenameElementQuickFix());
                return;
              }
            }
          }
        }
      }
    }
  }
}

