package com.jetbrains.python.inspections;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.AddFieldQuickFix;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyForStatementNavigator;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author oleg
 */
class PyUnusedLocalInspectionVisitor extends PyInspectionVisitor {
  private final boolean myIgnoreTupleUnpacking;
  private final boolean myIgnoreLambdaParameters;
  private final boolean myIgnoreRangeIterationVariables;
  private final HashSet<PsiElement> myUnusedElements;
  private final HashSet<PsiElement> myUsedElements;

  public PyUnusedLocalInspectionVisitor(final ProblemsHolder holder,
                                        boolean ignoreTupleUnpacking,
                                        boolean ignoreLambdaParameters,
                                        boolean ignoreRangeIterationVariables) {
    super(holder);
    myIgnoreTupleUnpacking = ignoreTupleUnpacking;
    myIgnoreLambdaParameters = ignoreLambdaParameters;
    myIgnoreRangeIterationVariables = ignoreRangeIterationVariables;
    myUnusedElements = new HashSet<PsiElement>();
    myUsedElements = new HashSet<PsiElement>();
  }

  @Override
  public void visitPyFunction(final PyFunction node) {
    processScope(node, node);
  }

  @Override
  public void visitPyLambdaExpression(final PyLambdaExpression node) {
    processScope(PsiTreeUtil.getParentOfType(node, ScopeOwner.class), node);
  }

  static class DontPerformException extends RuntimeException {}

  private void processScope(final ScopeOwner owner, final PyElement node) {
    if (owner.getContainingFile() instanceof PyExpressionCodeFragment || PydevConsoleRunner.isInPydevConsole(owner)){
      return;
    }

    if (callsLocals(owner)) return;

    // If method overrides others or is overridden, do not mark parameters as unused if they are
    final Scope scope = owner.getScope();
    final ControlFlow flow = owner.getControlFlow();
    final Instruction[] instructions = flow.getInstructions();

    // Iteration over write accesses
    for (int i = 0; i < instructions.length; i++) {
      final Instruction instruction = instructions[i];
      final PsiElement element = instruction.getElement();
      if (element instanceof PyFunction && owner instanceof PyFunction){
        if (!myUsedElements.contains(element)){
          myUnusedElements.add(element);
        }
      }
      else if (instruction instanceof ReadWriteInstruction) {
        final String name = ((ReadWriteInstruction)instruction).getName();
        // Ignore empty, wildcards or global names
        if (name == null || "_".equals(name) || scope.isGlobal(name)) {
          continue;
        }
        // Ignore elements out of scope
        if (element == null || !PsiTreeUtil.isAncestor(node, element, false)){
          continue;
        }
        // Ignore arguments of import statement
        if (PyImportStatementNavigator.getImportStatementByElement(element) != null) {
          continue;
        }
        if (element instanceof PyQualifiedExpression && ((PyQualifiedExpression)element).getQualifier() != null) {
          continue;
        }
        final ReadWriteInstruction.ACCESS access = ((ReadWriteInstruction)instruction).getAccess();
        // WriteAccess
        if (access.isWriteAccess()) {
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
        // Ignore elements out of scope
        if (element == null || !PsiTreeUtil.isAncestor(node, element, false)){
          continue;
        }
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
            for (ResolveResult result : ((PyReferenceExpression)element).getReference().multiResolve(false)) {
              final PsiElement resolveElement = result.getElement();
              // in case when we resolve out of the scope we still can have imported statements with write accesses inside
              if (resolveElement != null && !PsiTreeUtil.isAncestor(owner, resolveElement, false)){
                myUsedElements.add(element);
                myUsedElements.add(resolveElement);
                myUnusedElements.remove(element);
                myUnusedElements.remove(resolveElement);
              }
            }
          }
          ControlFlowUtil
            .iteratePrev(number, instructions, new Function<Instruction, ControlFlowUtil.Operation>() {
              public ControlFlowUtil.Operation fun(final Instruction inst) {
                final PsiElement element = inst.getElement();
                // Mark function as used
                if (element instanceof PyFunction){
                  if (name.equals(((PyFunction)element).getName())){
                    myUsedElements.add(element);
                    myUnusedElements.remove(element);
                    return ControlFlowUtil.Operation.CONTINUE;
                  }
                }
                // Mark write access as used
                else if (inst instanceof ReadWriteInstruction) {
                  final ReadWriteInstruction rwInstruction = (ReadWriteInstruction)inst;
                  if (!name.equals(rwInstruction.getName()) || !rwInstruction.getAccess().isWriteAccess()) {
                    return ControlFlowUtil.Operation.NEXT;
                  }
                  // Ignore elements out of scope
                  if (element == null || !PsiTreeUtil.isAncestor(node, element, false)) {
                    return ControlFlowUtil.Operation.CONTINUE;
                  }
                  myUsedElements.add(element);
                  myUnusedElements.remove(element);
                  // In case when assignment is inside try part and there are no except statements we should move further
                  final PyTryPart tryPart = PsiTreeUtil.getParentOfType(element, PyTryPart.class);
                  if (tryPart != null && ((PyTryExceptStatement)tryPart.getParent()).getExceptParts().length == 0) {
                    return ControlFlowUtil.Operation.NEXT;
                  }
                  return ControlFlowUtil.Operation.CONTINUE;
                }
                return ControlFlowUtil.Operation.NEXT;
              }
            });
        }
      }
    }
  }

  private static boolean callsLocals(final ScopeOwner owner) {
    try {
      owner.acceptChildren(new PyRecursiveElementVisitor(){
        @Override
        public void visitPyCallExpression(final PyCallExpression node) {
          if ("locals".equals(node.getCallee().getText())){
            throw new DontPerformException();
          }
        }

        @Override
        public void visitPyFunction(final PyFunction node) {
          // stop here
        }
      });
    }
    catch (DontPerformException e) {
      return true;
    }
    return false;
  }

  void registerProblems() {
    final UnusedLocalFilter[] filters = Extensions.getExtensions(UnusedLocalFilter.EP_NAME);
    // Register problems

    Set<PyFunction> functionsWithInheritors = new  HashSet<PyFunction>();

    for (PsiElement element : myUnusedElements) {
      boolean ignoreUnused = false;
      for (UnusedLocalFilter filter : filters) {
        if (filter.ignoreUnused(element)) {
          ignoreUnused = true;
        }
      }
      if (ignoreUnused) continue;

      // Local function
      if (element instanceof PyFunction){
        final PsiElement nameIdentifier = ((PyFunction)element).getNameIdentifier();
        registerWarning(nameIdentifier == null ? element : nameIdentifier,
                        PyBundle.message("INSP.unused.locals.local.function.isnot.used",
                        ((PyFunction)element).getName()));
      } 
      // Local variable or parameter
      else {
        String name = element.getText();
        if (element instanceof PyNamedParameter || element.getParent() instanceof PyNamedParameter) {
          PyNamedParameter namedParameter = element instanceof PyNamedParameter
                                            ? (PyNamedParameter) element
                                            : (PyNamedParameter) element.getParent();
          name = namedParameter.getName();
          // In case when function is declared inside the class first parameter is either self or class and shouldn't be processed
          final PsiElement parent = namedParameter.getParent();
          if (parent instanceof PyParameterList && ((PyParameterList)parent).getParameters()[0] == namedParameter &&
              PsiTreeUtil.getParentOfType(element, PyClass.class) != null){
            continue;
          }
          if (myIgnoreLambdaParameters && PsiTreeUtil.getParentOfType(element, Callable.class) instanceof PyLambdaExpression) {
            continue;
          }
          boolean isInitMethod = false;
          PyClass containingClass = null;
          PyParameterList paramList = PsiTreeUtil.getParentOfType(element, PyParameterList.class);
          if (paramList != null && paramList.getParent() instanceof PyFunction) {
            PyFunction func = (PyFunction) paramList.getParent();
            containingClass = func.getContainingClass();
            if (PyNames.INIT.equals(func.getName()) && containingClass != null) {
              isInitMethod = true;
            }
            else if (ignoreUnusedParameters(func, functionsWithInheritors)) {
              continue;
            }
          }
          LocalQuickFix[] fixes = isInitMethod
                                  ? new LocalQuickFix[] { new AddFieldQuickFix(name, containingClass, name) }
                                  : LocalQuickFix.EMPTY_ARRAY;
          registerWarning(element, PyBundle.message("INSP.unused.locals.parameter.isnot.used", name), fixes);
        }
        else {
          if (myIgnoreTupleUnpacking && isTupleUnpacking(element)) {
            continue;
          }
          final PyForStatement forStatement = PyForStatementNavigator.getPyForStatementByIterable(element);
          if (forStatement != null) {
            if (!myIgnoreRangeIterationVariables || !isRangeIteration(forStatement)) {
              registerProblem(element, PyBundle.message("INSP.unused.locals.local.variable.isnot.used", name),
                              ProblemHighlightType.LIKE_UNUSED_SYMBOL, null, new ReplaceWithWildCard());
            }
          }
          else {
            registerWarning(element, PyBundle.message("INSP.unused.locals.local.variable.isnot.used", name));
          }
        }
      }
    }
  }

  private boolean isRangeIteration(PyForStatement forStatement) {
    final PyExpression source = forStatement.getForPart().getSource();
    if (!(source instanceof PyCallExpression)) {
      return false;
    }
    PyCallExpression expr = (PyCallExpression) source;
    if (expr.isCalleeText("range", "xrange")) {
      final PyCallExpression.PyMarkedCallee callee = expr.resolveCallee(myTypeEvalContext);
      if (callee != null && !callee.isImplicitlyResolved() && PyBuiltinCache.getInstance(forStatement).hasInBuiltins(callee.getCallable())) {
        return true;
      }
    }
    return false;
  }

  private static boolean ignoreUnusedParameters(PyFunction func, Set<PyFunction> functionsWithInheritors) {
    if (functionsWithInheritors.contains(func)) {
      return true;
    }
    if (PySuperMethodsSearch.search(func).findFirst() != null ||
        PyOverridingMethodsSearch.search(func, true).findFirst() != null) {
      functionsWithInheritors.add(func);
      return true;
    }
    return false;
  }

  private boolean isTupleUnpacking(PsiElement element) {
    if (!(element instanceof PyTargetExpression)) {
      return false;
    }
    // Handling of the star expressions
    PsiElement parent = element.getParent();
    if (parent instanceof PyStarExpression){
      element = parent;
      parent = element.getParent();
    }
    if (parent instanceof PyTupleExpression) {
      // if all the items of the tuple are unused, we still highlight all of them; if some are unused, we ignore
      final PyTupleExpression tuple = (PyTupleExpression)parent;
      for (PyExpression expression : tuple.getElements()) {
        if (expression instanceof PyStarExpression){
          if (!myUnusedElements.contains(((PyStarExpression)expression).getExpression())){
            return true;
          }
        } else if (!myUnusedElements.contains(expression)) {
          return true;
        }
      }
    }
    return false;
  }

  private void registerWarning(@NotNull final PsiElement element, final String msg, LocalQuickFix... quickfixes) {
    registerProblem(element, msg, ProblemHighlightType.LIKE_UNUSED_SYMBOL, null, quickfixes);
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
      final PyFile pyFile = (PyFile) PyElementGenerator.getInstance(psiElement.getProject()).createDummyFile(LanguageLevel.getDefault(),
                                                                                                             "for _ in tuples:\n  pass"
      );
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
