/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.inspections.quickfix.AddFieldQuickFix;
import com.jetbrains.python.inspections.quickfix.PyRemoveParameterQuickFix;
import com.jetbrains.python.inspections.quickfix.PyRemoveStatementQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyForStatementNavigator;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author oleg
 */
public class PyUnusedLocalInspectionVisitor extends PyInspectionVisitor {
  private final boolean myIgnoreTupleUnpacking;
  private final boolean myIgnoreLambdaParameters;
  private final boolean myIgnoreRangeIterationVariables;
  private final HashSet<PsiElement> myUnusedElements;
  private final HashSet<PsiElement> myUsedElements;

  public PyUnusedLocalInspectionVisitor(@NotNull ProblemsHolder holder,
                                        @NotNull LocalInspectionToolSession session,
                                        boolean ignoreTupleUnpacking,
                                        boolean ignoreLambdaParameters,
                                        boolean ignoreRangeIterationVariables) {
    super(holder, session);
    myIgnoreTupleUnpacking = ignoreTupleUnpacking;
    myIgnoreLambdaParameters = ignoreLambdaParameters;
    myIgnoreRangeIterationVariables = ignoreRangeIterationVariables;
    myUnusedElements = new HashSet<>();
    myUsedElements = new HashSet<>();
  }

  @Override
  public void visitPyFunction(final PyFunction node) {
    processScope(node);
  }

  @Override
  public void visitPyLambdaExpression(final PyLambdaExpression node) {
    processScope(node);
  }

  @Override
  public void visitPyClass(PyClass node) {
    processScope(node);
  }

  private void processScope(final ScopeOwner owner) {
    if (owner.getContainingFile() instanceof PyExpressionCodeFragment || callsLocals(owner)) {
      return;
    }
    if (!(owner instanceof PyClass)) {
      collectAllWrites(owner);
    }
    collectUsedReads(owner);
  }

  private void collectAllWrites(ScopeOwner owner) {
    final Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
    for (Instruction instruction : instructions) {
      final PsiElement element = instruction.getElement();
      if (element instanceof PyFunction && owner instanceof PyFunction) {
        if (PyKnownDecoratorUtil.hasUnknownDecorator((PyFunction)element, myTypeEvalContext)) {
          continue;
        }
        if (!myUsedElements.contains(element)) {
          myUnusedElements.add(element);
        }
      }
      else if (instruction instanceof ReadWriteInstruction) {
        final ReadWriteInstruction readWriteInstruction = (ReadWriteInstruction)instruction;
        final ReadWriteInstruction.ACCESS access = readWriteInstruction.getAccess();
        if (!access.isWriteAccess()) {
          continue;
        }
        final String name = readWriteInstruction.getName();
        // Ignore empty, wildcards, global and nonlocal names
        final Scope scope = ControlFlowCache.getScope(owner);
        if (name == null || "_".equals(name) || scope.isGlobal(name) || scope.isNonlocal(name)) {
          continue;
        }
        // Ignore elements out of scope
        if (element == null || !PsiTreeUtil.isAncestor(owner, element, false)) {
          continue;
        }
        // Ignore arguments of import statement
        if (PyImportStatementNavigator.getImportStatementByElement(element) != null) {
          continue;
        }
        if (PyAugAssignmentStatementNavigator.getStatementByTarget(element) != null) {
          continue;
        }
        if (parameterInMethodWithFixedSignature(owner, element)) {
          continue;
        }
        if (!myUsedElements.contains(element)) {
          myUnusedElements.add(element);
        }
      }
    }
  }

  private static boolean parameterInMethodWithFixedSignature(@NotNull ScopeOwner owner, @NotNull PsiElement element) {
    if (owner instanceof PyFunction && element instanceof PyParameter) {
      final PyFunction function = (PyFunction)owner;
      final String functionName = function.getName();

      return !PyNames.INIT.equals(functionName) &&
             function.getContainingClass() != null &&
             PyNames.getBuiltinMethods(LanguageLevel.forElement(function)).containsKey(functionName);
    }

    return false;
  }

  private void collectUsedReads(final ScopeOwner owner) {
    final Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
    for (int i = 0; i < instructions.length; i++) {
      final Instruction instruction = instructions[i];
      if (instruction instanceof ReadWriteInstruction) {
        final ReadWriteInstruction readWriteInstruction = (ReadWriteInstruction)instruction;
        final ReadWriteInstruction.ACCESS access = readWriteInstruction.getAccess();
        if (!access.isReadAccess()) {
          continue;
        }
        final String name = readWriteInstruction.getName();
        if (name == null) {
          continue;
        }
        final PsiElement element = instruction.getElement();
        // Ignore elements out of scope
        if (element == null || !PsiTreeUtil.isAncestor(owner, element, false)) {
          continue;
        }
        final int startInstruction;
        if (access.isWriteAccess()) {
          final PyAugAssignmentStatement augAssignmentStatement = PyAugAssignmentStatementNavigator.getStatementByTarget(element);
          startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, augAssignmentStatement);
        }
        else {
          startInstruction = i;
        }
        // Check if the element is declared out of scope, mark all out of scope write accesses as used
        if (element instanceof PyReferenceExpression) {
          final PyReferenceExpression ref = (PyReferenceExpression)element;
          final ScopeOwner declOwner = ScopeUtil.getDeclarationScopeOwner(ref, name);
          if (declOwner != null && declOwner != owner) {
            Collection<PsiElement> writeElements = ScopeUtil.getReadWriteElements(name, declOwner, false, true);
            for (PsiElement e : writeElements) {
              myUsedElements.add(e);
              myUnusedElements.remove(e);
            }
          }
        }
        ControlFlowUtil.iteratePrev(startInstruction, instructions, inst -> {
          final PsiElement element1 = inst.getElement();
          // Mark function as used
          if (element1 instanceof PyFunction) {
            if (name.equals(((PyFunction)element1).getName())){
              myUsedElements.add(element1);
              myUnusedElements.remove(element1);
              return ControlFlowUtil.Operation.CONTINUE;
            }
          }
          // Mark write access as used
          else if (inst instanceof ReadWriteInstruction) {
            final ReadWriteInstruction rwInstruction = (ReadWriteInstruction)inst;
            if (rwInstruction.getAccess().isWriteAccess() && name.equals(rwInstruction.getName())) {
              // For elements in scope
              if (element1 != null && PsiTreeUtil.isAncestor(owner, element1, false)) {
                myUsedElements.add(element1);
                myUnusedElements.remove(element1);
              }
              return ControlFlowUtil.Operation.CONTINUE;
            }
          }
          return ControlFlowUtil.Operation.NEXT;
        });
      }
    }
  }

  static class DontPerformException extends RuntimeException {}

  private static boolean callsLocals(final ScopeOwner owner) {
    try {
      owner.acceptChildren(new PyRecursiveElementVisitor(){
        @Override
        public void visitPyCallExpression(final PyCallExpression node) {
          final PyExpression callee = node.getCallee();
          if (callee != null && "locals".equals(callee.getName())){
            throw new DontPerformException();
          }
          node.acceptChildren(this); // look at call expr in arguments
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
    final PyInspectionExtension[] filters = Extensions.getExtensions(PyInspectionExtension.EP_NAME);
    // Register problems

    final Set<PyFunction> functionsWithInheritors = new HashSet<>();
    final Map<PyFunction, Boolean> emptyFunctions = new HashMap<>();

    for (PsiElement element : myUnusedElements) {
      boolean ignoreUnused = false;
      for (PyInspectionExtension filter : filters) {
        if (filter.ignoreUnused(element)) {
          ignoreUnused = true;
        }
      }
      if (ignoreUnused) continue;

      if (element instanceof PyFunction) {
        // Local function
        final PsiElement nameIdentifier = ((PyFunction)element).getNameIdentifier();
        registerWarning(nameIdentifier == null ? element : nameIdentifier,
                        PyBundle.message("INSP.unused.locals.local.function.isnot.used",
                        ((PyFunction)element).getName()), new PyRemoveStatementQuickFix());
      }
      else if (element instanceof PyClass) {
        // Local class
        final PyClass cls = (PyClass)element;
        final PsiElement name = cls.getNameIdentifier();
        registerWarning(name != null ? name : element,
                        PyBundle.message("INSP.unused.locals.local.class.isnot.used", cls.getName()), new PyRemoveStatementQuickFix());
      }
      else {
        // Local variable or parameter
        String name = element.getText();
        if (element instanceof PyNamedParameter || element.getParent() instanceof PyNamedParameter) {
          PyNamedParameter namedParameter = element instanceof PyNamedParameter
                                            ? (PyNamedParameter) element
                                            : (PyNamedParameter) element.getParent();
          name = namedParameter.getName();
          // When function is inside a class, first parameter may be either self or cls which is always 'used'.
          if (namedParameter.isSelf()) {
            continue;
          }
          if (myIgnoreLambdaParameters && PsiTreeUtil.getParentOfType(element, PyCallable.class) instanceof PyLambdaExpression) {
            continue;
          }
          boolean mayBeField = false;
          PyClass containingClass = null;
          PyParameterList paramList = PsiTreeUtil.getParentOfType(element, PyParameterList.class);
          if (paramList != null && paramList.getParent() instanceof PyFunction) {
            final PyFunction func = (PyFunction) paramList.getParent();
            containingClass = func.getContainingClass();
            if (PyNames.INIT.equals(func.getName()) && containingClass != null) {
              if (!namedParameter.isKeywordContainer() && !namedParameter.isPositionalContainer()) {
                mayBeField = true;
              }
            }
            else if (ignoreUnusedParameters(func, functionsWithInheritors)) {
              continue;
            }
            if (func.asMethod() != null) {
              Boolean isEmpty = emptyFunctions.get(func);
              if (isEmpty == null) {
                isEmpty = isEmptyFunction(func);
                emptyFunctions.put(func, isEmpty);
              }
              if (isEmpty && !mayBeField) {
                continue;
              }
            }
          }
          boolean canRemove = !(PsiTreeUtil.getPrevSiblingOfType(element, PyParameter.class) instanceof PySingleStarParameter) ||
            PsiTreeUtil.getNextSiblingOfType(element, PyParameter.class) != null;

          final List<LocalQuickFix> fixes = new ArrayList<>();
          if (mayBeField) {
            fixes.add(new AddFieldQuickFix(name, name, containingClass.getName(), false));
          }
          if (canRemove) {
            fixes.add(new PyRemoveParameterQuickFix());
          }
          registerWarning(element, PyBundle.message("INSP.unused.locals.parameter.isnot.used", name), fixes.toArray(new LocalQuickFix[fixes.size()]));
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
            registerWarning(element, PyBundle.message("INSP.unused.locals.local.variable.isnot.used", name), new PyRemoveStatementQuickFix());
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
      final PyCallable callee = expr.resolveCalleeFunction(PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext));
      if (callee != null && PyBuiltinCache.getInstance(forStatement).isBuiltin(callee)) {
        return true;
      }
    }
    return false;
  }

  private boolean ignoreUnusedParameters(PyFunction func, Set<PyFunction> functionsWithInheritors) {
    if (functionsWithInheritors.contains(func)) {
      return true;
    }
    if (PySuperMethodsSearch.search(func, myTypeEvalContext).findFirst() != null ||
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
      if (!FileModificationService.getInstance().preparePsiElementForWrite(descriptor.getPsiElement())) {
        return;
      }
      replace(descriptor.getPsiElement());
    }

    private void replace(final PsiElement psiElement) {
      final PyFile pyFile = (PyFile) PyElementGenerator.getInstance(psiElement.getProject()).createDummyFile(LanguageLevel.getDefault(),
                                                                                                             "for _ in tuples:\n  pass"
      );
      final PyExpression target = ((PyForStatement)pyFile.getStatements().get(0)).getForPart().getTarget();
      CommandProcessor.getInstance().executeCommand(psiElement.getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
        if (target != null) {
          psiElement.replace(target);
        }
      }), getName(), null);
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }

  private static boolean isEmptyFunction(@NotNull PyFunction f) {
    final PyStatementList statementList = f.getStatementList();
    final PyStatement[] statements = statementList.getStatements();
    if (statements.length == 0) {
      return true;
    }
    else if (statements.length == 1) {
      if (isStringLiteral(statements[0]) || isPassOrRaiseOrEmptyReturn(statements[0])) {
        return true;
      }
    }
    else if (statements.length == 2) {
      if (isStringLiteral(statements[0]) && (isPassOrRaiseOrEmptyReturn(statements[1]))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isPassOrRaiseOrEmptyReturn(PyStatement stmt) {
    if (stmt instanceof PyPassStatement || stmt instanceof PyRaiseStatement) {
      return true;
    }
    if (stmt instanceof PyReturnStatement && ((PyReturnStatement)stmt).getExpression() == null) {
      return true;
    }
    return false;
  }

  private static boolean isStringLiteral(PyStatement stmt) {
    if (stmt instanceof PyExpressionStatement) {
      final PyExpression expr = ((PyExpressionStatement)stmt).getExpression();
      if (expr instanceof PyStringLiteralExpression) {
        return true;
      }
    }
    return false;
  }
}
