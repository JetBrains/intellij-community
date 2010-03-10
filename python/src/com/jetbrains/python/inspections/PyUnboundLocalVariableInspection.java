package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.AddGlobalQuickFix;
import com.jetbrains.python.actions.AddImportAction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator;
import com.jetbrains.python.psi.impl.PyGlobalStatementNavigator;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class PyUnboundLocalVariableInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @NotNull
  @Nls
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unbound");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "PyUnboundLocalVariableInspection";
  }

  public boolean isEnabledByDefault() {
    return false;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PyInspectionVisitor(holder){
      @Override
      public void visitPyReferenceExpression(final PyReferenceExpression node) {
        // Ignore callee expressions
        if (PyCallExpressionNavigator.getPyCallExpressionByCallee(node) != null){
          return;
        }
        // Ignore global statements arguments
        if (PyGlobalStatementNavigator.getPyGlobalStatementByArgument(node) != null){
          return;
        }
        // Ignore qualifier inspections
        final PyExpression qualifier = node.getQualifier();
        if (qualifier != null){
          qualifier.accept(this);
          return;
        }
        // Ignore import arguments
        if (PyImportStatementNavigator.getImportStatementByElement(node) != null){
          return;
        }
        final ScopeOwner owner = PsiTreeUtil.getParentOfType(node, ScopeOwner.class);
        if (owner == null){
          return;
        }
        final Scope scope = owner.getScope();
        final String name = node.getReferencedName();
        // Ignore globals
        if (scope.isGlobal(name)){
          return;
        }
        final ScopeVariable variable = scope.getDeclaredVariable(node, name);
        if (variable == null) {
          if (owner instanceof PyClass || owner instanceof PyFunction){
          registerProblem(node, PyBundle.message("INSP.unbound.local.variable", node.getName()),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                          null,
                          new AddGlobalQuickFix());
          } else {
            registerProblem(node, PyBundle.message("INSP.unbound.name.not.defined", node.getName()));
          }
        }
      }
    };
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

}