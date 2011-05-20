package com.jetbrains.python.inspections;

import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.AddGlobalQuickFix;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class PyUnboundLocalVariableInspection extends PyInspection {
  @NotNull
  @Nls
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unbound");
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PyInspectionVisitor(holder){
      @Override
      public void visitPyReferenceExpression(final PyReferenceExpression node) {
        if (node.getContainingFile() instanceof PyExpressionCodeFragment || PydevConsoleRunner.isInPydevConsole(node)){
          return;
        }
        // Ignore global statements arguments
        if (PyGlobalStatementNavigator.getByArgument(node) != null){
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
        final ScopeOwner owner = ScopeUtil.getDeclarationScopeOwner(node, node.getName());
        if (owner == null) {
          return;
        }
        // Check if there is a non-toplevel declaration for nonlocal
        final ScopeOwner immediateOwner = ScopeUtil.getScopeOwner(node);
        final Scope immediateScope = ControlFlowCache.getScope(immediateOwner);
        if (immediateScope.isNonlocal(node.getName()) && owner instanceof PyFile) {
          registerUnboundLocal(node);
          return;
        }
        // Ignore references declared in outer scopes
        if (owner != PsiTreeUtil.getParentOfType(node, ScopeOwner.class)) {
          return;
        }
        final String name = node.getReferencedName();
        if (name == null) {
          return;
        }
        final Scope scope = ControlFlowCache.getScope(owner);
        // Ignore globals and if scope even doesn't contain such a declaration
        if (scope.isGlobal(name) || (!scope.containsDeclaration(name))){
          return;
        }
        final ScopeVariable variable = scope.getDeclaredVariable(node, name);
        if (variable == null) {
          boolean resolves2LocalVariable = false;
          boolean resolve2Scope = true;
          for (ResolveResult result : node.getReference().multiResolve(true)) {
            final PsiElement element = result.getElement();
            if (element == null){
              continue;
            }
            // Ignore builtin elements here
            final PsiFile containingFile = element.getContainingFile();
            if (containingFile != null) {
              final String fileName = containingFile.getName();
              if (PyBuiltinCache.BUILTIN_FILE.equals(fileName) || PyBuiltinCache.BUILTIN_FILE_3K.equals(fileName)){
                continue;
              }
            }
            if (PyAssignmentStatementNavigator.getStatementByTarget(element) != null || 
                PyForStatementNavigator.getPyForStatementByIterable(element) != null ||
                PyExceptPartNavigator.getPyExceptPartByTarget(element) != null ||
                PyListCompExpressionNavigator.getPyListCompExpressionByVariable(element) != null) {
              resolves2LocalVariable = true;
              resolve2Scope = PsiTreeUtil.isAncestor(owner, element, false);
              break;
            }
          }
          // Ignore this if can resolve not to local variable
          if (!resolves2LocalVariable) {
            return;
          }

          final Ref<Boolean> readAccessSeen = new Ref<Boolean>(false);
          final Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
          final int number = ControlFlowUtil.findInstructionNumberByElement(instructions, node);
          // Do not perform this check if we cannot find corresponding instruction
          if (number != -1) {
            ControlFlowUtil.iteratePrev(number, instructions, new Function<Instruction, ControlFlowUtil.Operation>() {
              public ControlFlowUtil.Operation fun(final Instruction inst) {
                if (inst.num() == number){
                  return ControlFlowUtil.Operation.NEXT;
                }
                if (inst instanceof ReadWriteInstruction) {
                  final ReadWriteInstruction rwInst = (ReadWriteInstruction)inst;
                  if (name.equals(rwInst.getName())) {
                    if (scope.getDeclaredVariable(inst.getElement(), name) != null) {
                      return ControlFlowUtil.Operation.BREAK;
                    }
                    if (rwInst.getAccess().isWriteAccess()) {
                      return ControlFlowUtil.Operation.CONTINUE;
                    }
                    else {
                      readAccessSeen.set(true);
                      return ControlFlowUtil.Operation.BREAK;
                    }
                  }
                }
                return ControlFlowUtil.Operation.NEXT;
              }
            });
          }
          // In this case we've already inspected prev read access and shouldn't warn about this one
          if (readAccessSeen.get()){
            return;
          }
          if (resolve2Scope){
            if (owner instanceof PyFile){
              registerProblem(node, PyBundle.message("INSP.unbound.name.not.defined", node.getName()));
            }
            else {
              registerUnboundLocal(node);
            }
          } else
          if (owner instanceof PyFunction && PsiTreeUtil.getParentOfType(owner, PyClass.class, PyFile.class) instanceof PyFile){
            registerUnboundLocal(node);
          }
        }
      }

      private void registerUnboundLocal(PyReferenceExpression node) {
        registerProblem(node, PyBundle.message("INSP.unbound.local.variable", node.getName()),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        null,
                        new AddGlobalQuickFix());

      }
    };
  }
}
