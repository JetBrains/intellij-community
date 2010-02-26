package com.jetbrains.python.inspections;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.PyControlFlowUtil;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;

import java.util.HashSet;

/**
 * @author oleg
 */
class PyUnusedLocalVariableInspectionVisitor extends PyInspectionVisitor {
  private HashSet<PsiElement> myUnusedElements;

  public PyUnusedLocalVariableInspectionVisitor(final ProblemsHolder holder) {
    super(holder);
    myUnusedElements = new HashSet<PsiElement>();
  }

  @Override
  public void visitPyFile(final PyFile node) {
    node.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        ProgressManager.checkCanceled();
        super.visitElement(element);
      }

      // We check only functions content due to language semantics here
      // However nested scopes are OK.
      @Override
      public void visitPyFunction(final PyFunction node) {
        processScope(node);
        super.visitPyFunction(node);
      }
    });
    registerProblems();
  }


  private void processScope(final ScopeOwner owner) {
    // TODO[oleg] Do not show warning in python code expression mode (evaluate in debug or watches)

    // If method overrides others do not mark parameters as unused if they are
    boolean parametersCanBeUnused = false;
    if (owner instanceof PyFunction) {
      parametersCanBeUnused = PySuperMethodsSearch.search(((PyFunction)owner)).findFirst() != null;
    }

    final ControlFlow flow = owner.getControlFlow();
    final Instruction[] instructions = flow.getInstructions();

    // Iteration over write accesses
    for (int i = 0; i < instructions.length; i++) {
      final Instruction instruction = instructions[i];
      if (instruction instanceof ReadWriteInstruction) {
        final String name = ((ReadWriteInstruction)instruction).getName();
        if (name == null) {
          continue;
        }
        final PsiElement element = instruction.getElement();
        // Ignore arguments of import statement
        if (PyImportStatementNavigator.getImportStatementByElement(element) != null) {
          continue;
        }
        final ReadWriteInstruction.ACCESS access = ((ReadWriteInstruction)instruction).getAccess();
        // WriteAccess
        if (access.isWriteAccess() && (parametersCanBeUnused || !(element != null && element.getParent() instanceof PyNamedParameter))) {
          myUnusedElements.add(element);
        }
      }
    }

    // Iteration over read accesses
    for (int i = 0; i < instructions.length; i++) {
      final Instruction instruction = instructions[i];
      if (instruction instanceof ReadWriteInstruction) {
        final String name = ((ReadWriteInstruction)instruction).getName();
        if (name == null) {
          continue;
        }
        final PsiElement element = instruction.getElement();
        final ReadWriteInstruction.ACCESS access = ((ReadWriteInstruction)instruction).getAccess();
        // Read or self assign access
        if (access.isReadAccess()) {
          int number = i;
          if (access == ReadWriteInstruction.ACCESS.READWRITE) {
            final PyAugAssignmentStatement augAssignmentStatement = PyAugAssignmentStatementNavigator.getStatementByTarget(element);
            number = ControlFlowUtil.findInstructionNumberByElement(instructions, augAssignmentStatement);
          }

          // Check out of scope resolve elements, processes nested scopes
          if (element instanceof PyReferenceExpression){
            boolean outOfScope = false;
            for (ResolveResult result : ((PyReferenceExpression)element).multiResolve(false)) {
              final PsiElement resolveElement = result.getElement();
              if (!PsiTreeUtil.isAncestor(owner, resolveElement, false)){
                outOfScope = true;
                myUnusedElements.remove(resolveElement);
              }
            }
            if (outOfScope){
              continue;
            }
          }

          PyControlFlowUtil
            .iterateWriteAccessFor(name, number, instructions, new Function<ReadWriteInstruction, PyControlFlowUtil.Operation>() {
              public PyControlFlowUtil.Operation fun(final ReadWriteInstruction rwInstr) {
                final PsiElement instrElement = rwInstr.getElement();
                myUnusedElements.remove(instrElement);
                return PyControlFlowUtil.Operation.CONTINUE;
              }
            });
        }
      }
    }
  }

  private void registerProblems() {
    // Register problems
    for (PsiElement element : myUnusedElements) {
      final String name = element.getText();
      // Ignore unused self parameters as obligatory
      if (PyPsiUtils.isMethodContext(element) && "self".equals(name)) {
        continue;
      }
      if (element instanceof PyNamedParameter) {
        registerWarning(element, PyBundle.message("INSP.unused.locals.parameter.isnot.used", name));
      }
      else {
        registerWarning(element, PyBundle.message("INSP.unused.locals.local.variable.isnot.used", name));
      }
    }
  }

  private void registerWarning(final PsiElement element, final String msg) {
    registerProblem(element, msg, ProblemHighlightType.LIKE_UNUSED_SYMBOL, null);
  }
}
