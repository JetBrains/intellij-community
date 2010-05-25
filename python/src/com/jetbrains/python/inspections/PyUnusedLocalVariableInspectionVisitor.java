package com.jetbrains.python.inspections;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.PyControlFlowUtil;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.impl.PyForStatementNavigator;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author oleg
 */
class PyUnusedLocalVariableInspectionVisitor extends PyInspectionVisitor {
  private final HashSet<PsiElement> myUnusedElements;
  private final HashSet<PsiElement> myUsedElements;

  public PyUnusedLocalVariableInspectionVisitor(final ProblemsHolder holder) {
    super(holder);
    myUnusedElements = new HashSet<PsiElement>();
    myUsedElements = new HashSet<PsiElement>();
  }

  @Override
  public void visitPyFunction(final PyFunction node) {
    processScope(node);
  }

  class DontPerformException extends RuntimeException {}

  private void processScope(final ScopeOwner owner) {
    if (owner.getContainingFile() instanceof PyExpressionCodeFragment || PydevConsoleRunner.isInPydevConsole(owner)){
      return;
    }
    // Check for locals() call
    try {
      owner.accept(new PyRecursiveElementVisitor(){
        @Override
        public void visitPyCallExpression(final PyCallExpression node) {
          if ("locals".equals(node.getCallee().getText())){
            throw new DontPerformException();
          }
        }
      });
    }
    catch (DontPerformException e) {
      return;
    }

    // If method overrides others do not mark parameters as unused if they are
    boolean parametersCanBeUnused = false;
    if (owner instanceof PyFunction) {
      parametersCanBeUnused = PySuperMethodsSearch.search(((PyFunction)owner)).findFirst() != null;
    }

    final Scope scope = owner.getScope();
    final ControlFlow flow = owner.getControlFlow();
    final Instruction[] instructions = flow.getInstructions();

    // Iteration over write accesses
    for (int i = 0; i < instructions.length; i++) {
      final Instruction instruction = instructions[i];
      if (instruction instanceof ReadWriteInstruction) {
        final String name = ((ReadWriteInstruction)instruction).getName();
        // Ignore empty, wildcards or global names
        if (name == null || "_".equals(name) || scope.isGlobal(name)) {
          continue;
        }
        final PsiElement element = instruction.getElement();
        // Ignore arguments of import statement
        if (PyImportStatementNavigator.getImportStatementByElement(element) != null) {
          continue;
        }
        if (element instanceof PyQualifiedExpression && ((PyQualifiedExpression)element).getQualifier() != null) {
          continue;
        }
        final ReadWriteInstruction.ACCESS access = ((ReadWriteInstruction)instruction).getAccess();
        // WriteAccess
        if (access.isWriteAccess() && (parametersCanBeUnused || !(element != null && element.getParent() instanceof PyNamedParameter))) {
          if (!myUsedElements.contains(element)){
            myUnusedElements.add(element);
          }
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
            for (ResolveResult result : ((PyReferenceExpression)element).getReference().multiResolve(false)) {
              final PsiElement resolveElement = result.getElement();
              if (!PsiTreeUtil.isAncestor(owner, resolveElement, false)){
                outOfScope = true;
                myUsedElements.add(element);
                myUsedElements.add(resolveElement);
                myUnusedElements.remove(element);
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
                myUsedElements.add(instrElement);
                myUnusedElements.remove(instrElement);
                return PyControlFlowUtil.Operation.CONTINUE;
              }
            });
        }
      }
    }
  }

  void registerProblems() {
    // Register problems
    for (PsiElement element : myUnusedElements) {
      final String name = element.getText();
      if (element instanceof PyNamedParameter) {
        // Ignore unused self parameters as obligatory
        if ("self".equals(name) && PyPsiUtils.isMethodContext(element)) {
          continue;
        }
        // cls for @classmethod decorated methods
        if ("cls".equals(name)) {
          final Set<PyFunction.Flag> flagSet = PyUtil.detectDecorationsAndWrappersOf(PsiTreeUtil.getParentOfType(element, PyFunction.class));
          if (flagSet.contains(PyFunction.Flag.CLASSMETHOD)) {
            continue;
          }
        }
        registerWarning(element, PyBundle.message("INSP.unused.locals.parameter.isnot.used", name));
      }
      else {
        if (PyForStatementNavigator.getPyForStatementByIterable(element) != null){
          registerProblem(element, PyBundle.message("INSP.unused.locals.local.variable.isnot.used", name),
                          ProblemHighlightType.LIKE_UNUSED_SYMBOL, null, new ReplaceWithWildCard());
        } else {
          registerWarning(element, PyBundle.message("INSP.unused.locals.local.variable.isnot.used", name));
        }
      }
    }
  }

  private void registerWarning(final PsiElement element, final String msg) {
    registerProblem(element, msg, ProblemHighlightType.LIKE_UNUSED_SYMBOL, null);
  }

  private static class ReplaceWithWildCard implements LocalQuickFix {
    @NotNull
    public String getName() {
      return PyBundle.message("INSP.unused.locals.replace.with.wildcard");
    }

    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      if (!CodeInsightUtilBase.preparePsiElementForWrite(descriptor.getPsiElement())) {
        return;
      }
      replace(descriptor.getPsiElement());
    }

    private void replace(final PsiElement psiElement) {
      final PyFile pyFile = (PyFile) PyElementGenerator.getInstance(psiElement.getProject()).createDummyFile("for _ in tuples:\n  pass");
      final PyExpression target = ((PyForStatement)pyFile.getStatements().get(0)).getForPart().getTarget();
      CommandProcessor.getInstance().executeCommand(psiElement.getProject(), new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              psiElement.replace(target);
            }
          });
        }
      }, getName(), null);
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }
}
