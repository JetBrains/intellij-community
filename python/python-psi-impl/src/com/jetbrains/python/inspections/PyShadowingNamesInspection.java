// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Warns about shadowing names defined in outer scopes.
 *
 */
@ApiStatus.Internal
public final class PyShadowingNamesInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
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
                registerProblem(problemElement, PyPsiBundle.problemMessage("INSP.shadows.name.from.outer.scope", name),
                                ProblemHighlightType.WEAK_WARNING,
                                LocalQuickFix.notNullElements(PythonUiService.getInstance().createPyRenameElementQuickFix(problemElement)));
                return;
              }
            }
          }
        }
      }
    }
  }
}

