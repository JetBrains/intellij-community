package com.jetbrains.python.inspections;

import com.intellij.codeInsight.dataflow.DFALimitExceededException;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.cython.CythonLanguageDialect;
import com.jetbrains.mako.MakoLanguage;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.AddGlobalQuickFix;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyGlobalStatementNavigator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author oleg
 */
public class PyUnboundLocalVariableInspection extends PyInspection {
  private static Key<Set<ScopeOwner>> LARGE_FUNCTIONS_KEY = Key.create("PyUnboundLocalVariableInspection.LargeFunctions");

  @NotNull
  @Nls
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unbound");
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull final LocalInspectionToolSession session) {
    session.putUserData(LARGE_FUNCTIONS_KEY, new HashSet<ScopeOwner>());
    return new PyInspectionVisitor(holder, session) {
      @Override
      public void visitPyReferenceExpression(final PyReferenceExpression node) {
        if (CythonLanguageDialect._isDisabledFor(node) || MakoLanguage._isDisabledFor(node)) {
          return;
        }
        if (node.getContainingFile() instanceof PyExpressionCodeFragment || PydevConsoleRunner.isInPydevConsole(node)) {
          return;
        }
        // Ignore global statements arguments
        if (PyGlobalStatementNavigator.getByArgument(node) != null) {
          return;
        }
        // Ignore qualifier inspections
        if (node.getQualifier() != null) {
          return;
        }
        // Ignore import subelements
        if (PsiTreeUtil.getParentOfType(node, PyImportStatementBase.class) != null) {
          return;
        }
        final String name = node.getReferencedName();
        if (name == null) {
          return;
        }
        final ScopeOwner owner = ScopeUtil.getDeclarationScopeOwner(node, name);
        final Set<ScopeOwner> largeFunctions = session.getUserData(LARGE_FUNCTIONS_KEY);
        assert largeFunctions != null;
        if (owner == null || largeFunctions.contains(owner)) {
          return;
        }
        // Ignore references declared in outer scopes
        if (owner != ScopeUtil.getScopeOwner(node)) {
          return;
        }
        final Scope scope = ControlFlowCache.getScope(owner);
        // Ignore globals and if scope even doesn't contain such a declaration
        if (scope.isGlobal(name) || (!scope.containsDeclaration(name))){
          return;
        }
        // Start DFA from the assignment statement in case of augmented assignments
        final PsiElement anchor;
        final PyAugAssignmentStatement augAssignment = PsiTreeUtil.getParentOfType(node, PyAugAssignmentStatement.class);
        if (augAssignment != null && name.equals(augAssignment.getTarget().getName())) {
          anchor = augAssignment;
        }
        else {
          anchor = node;
        }
        final ScopeVariable variable;
        try {
          variable = scope.getDeclaredVariable(anchor, name);
        }
        catch (DFALimitExceededException e) {
          largeFunctions.add(owner);
          registerLargeFunction(owner);
          return;
        }
        if (variable == null) {
          final PsiElement resolved = node.getReference(resolveWithoutImplicits()).resolve();
          final boolean isBuiltin = PyBuiltinCache.getInstance(node).hasInBuiltins(resolved);
          if (owner instanceof PyClass) {
            if (isBuiltin || ScopeUtil.getDeclarationScopeOwner(owner, name) != null) {
              return;
            }
          }
          if (owner instanceof PyFile) {
            if (isBuiltin) {
              return;
            }
            registerProblem(node, PyBundle.message("INSP.unbound.name.not.defined", name));
          }
          else {
            registerProblem(node, PyBundle.message("INSP.unbound.local.variable", node.getName()),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            null,
                            new AddGlobalQuickFix());
          }
        }
      }

      @Override
      public void visitPyNonlocalStatement(final PyNonlocalStatement node) {
        if (CythonLanguageDialect._isDisabledFor(node)) {
          return;
        }
        for (PyTargetExpression var : node.getVariables()) {
          final String name = var.getName();
          final ScopeOwner owner = ScopeUtil.getDeclarationScopeOwner(var, name);
          if (owner == null || owner instanceof PyFile) {
            registerProblem(var, PyBundle.message("INSP.unbound.nonlocal.variable", name),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null);
          }
        }
      }

      private void registerLargeFunction(ScopeOwner owner) {
        registerProblem((owner instanceof PyFunction) ? ((PyFunction)owner).getNameIdentifier() : owner,
                        PyBundle.message("INSP.unbound.function.too.large", owner.getName()),
                        ProblemHighlightType.WEAK_WARNING,
                        null);
      }
    };
  }
}
