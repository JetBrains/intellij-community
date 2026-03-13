// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.inspections.quickfix.PySuppressInspectionFix;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Set;

/**
 * Detects global variable redeclaration in Jupyter notebooks.
 * <p>
 * TODO PY-85045: this inspection is disabled by default and needs further refinement
 */
public final class PyGlobalVariableRedeclarationInNotebookInspection extends PyInspection {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                  boolean isOnTheFly,
                                                  @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {
    private static final Set<String> PLOTTING_VARIABLE_NAMES = Set.of(
      "fig", "ax", "axs", "axes",
      "g", "grid",
      "p", "plot"
    );

    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyFile(@NotNull PyFile file) {
      if (!isNotebook(file)) {
        return;
      }
      super.visitPyFile(file);
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      if (node.isQualified() || PyNames.UNDERSCORE.equals(node.getName())) {
        return;
      }

      final String name = node.getName();
      if (name == null) {
        return;
      }

      final ScopeOwner owner = ScopeUtil.getScopeOwner(node);
      if (!(owner instanceof PyFile pyFile)) {
        return;
      }

      if (isSelfReference(node, name) || isDefinedInLoop(node) || isPlottingVariable(node, name, myTypeEvalContext)) {
        return;
      }

      final PyTargetExpression firstDefinition = pyFile.findTopLevelAttribute(name);
      if (firstDefinition != null && firstDefinition != node) {
        final PsiElement identifier = node.getNameIdentifier();
        final @InspectionMessage String message = PyPsiBundle.message(
          "INSP.global.variable.redeclaration.in.notebook",
          name
        );

        registerProblem(
          identifier != null ? identifier : node,
          message,
          ProblemHighlightType.WEAK_WARNING,
          null,
          new PySuppressInspectionFix("PyGlobalVariableRedeclarationInNotebook",
                                      PyPsiBundle.message("INSP.python.suppressor.suppress.for.statement"),
                                      PyStatement.class)
        );
      }
    }

    private static boolean isNotebook(@NotNull PyFile file) {
      return "ipynb".equals(FileUtilRt.getExtension(file.getName()));
    }

    private static boolean isDefinedInLoop(@NotNull PyTargetExpression node) {
      PyForStatement forStatement = PsiTreeUtil.getParentOfType(node, PyForStatement.class);
      if (forStatement == null) {
        return false;
      }
      PsiElement loopTarget = forStatement.getForPart().getTarget();
      return loopTarget != null && PsiTreeUtil.isAncestor(loopTarget, node, false);
    }

    private static boolean isSelfReference(@NotNull PyTargetExpression node, @NotNull String name) {
      PsiElement parent = node.getParent();
      if (parent == null) {
        return false;
      }
      return ContainerUtil.exists(PsiTreeUtil.findChildrenOfType(parent, PyReferenceExpression.class),
                                  ref -> name.equals(ref.getName()) && ref.getQualifier() == null);
    }

    /**
     * Heuristic: plotting objects are frequently and safely redefined in notebooks.
     */
    private static boolean isPlottingVariable(@NotNull PyTargetExpression target,
                                              @NotNull String name,
                                              @NotNull TypeEvalContext context) {
      if (PLOTTING_VARIABLE_NAMES.contains(name)) {
        return true;
      }

      PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(target, PyAssignmentStatement.class);
      if (assignment == null) {
        return false;
      }

      PyExpression value = assignment.getAssignedValue();
      if (value == null) {
        return false;
      }

      PyType valueType = context.getType(value);
      if (!(valueType instanceof PyClassType classType)) {
        return false;
      }

      String qName = classType.getClassQName();
      if (qName == null) {
        return false;
      }

      boolean isMatplotlibFigureOrAxes = qName.startsWith("matplotlib") && (qName.contains("Figure") || qName.contains("axes"));
      boolean isPlotlyFigure = qName.startsWith("plotly") && qName.contains("Figure");
      return isMatplotlibFigureOrAxes || isPlotlyFigure;
    }
  }
}