package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.AddGlobalQuickFix;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
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
        final String name = node.getReferencedName();
        final Scope scope = owner.getScope();
        // Ignore globals and if scope even doesn't contain such a declaration
        if (scope.isGlobal(name) || (!scope.containsDeclaration(name))){
          return;
        }
        final ScopeVariable variable = scope.getDeclaredVariable(node, name);
        if (variable == null) {
          boolean resolves2LocalVariable = false;
          boolean resolve2Scope = true;
          for (ResolveResult result : node.multiResolve(true)) {
            final PsiElement element = result.getElement();
            if (element == null){
              continue;
            }
            // Ingore builtin elements here
            final String fileName = element.getContainingFile().getName();
            if (PyBuiltinCache.BUILTIN_FILE.equals(fileName) || PyBuiltinCache.BUILTIN_FILE_3K.equals(fileName)){
              continue;
            }
            if (PyAssignmentStatementNavigator.getStatementByTarget(element)!=null){
              resolves2LocalVariable = true;
              resolve2Scope = PsiTreeUtil.isAncestor(owner, element, false);
              break;
            }
          }
          // Ignore this if can resolve not to local variable
          if (resolves2LocalVariable) {
            if (owner instanceof PyClass || owner instanceof PyFunction){
              registerProblem(node, PyBundle.message("INSP.unbound.local.variable", node.getName()),
                              ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                              null,
                              new AddGlobalQuickFix());
            } else {
              if (resolve2Scope){
                registerProblem(node, PyBundle.message("INSP.unbound.name.not.defined", node.getName()));
              }
            }
          }}
      }
    };
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

}