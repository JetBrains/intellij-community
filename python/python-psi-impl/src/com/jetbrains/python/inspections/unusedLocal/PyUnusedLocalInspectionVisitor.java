// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unusedLocal;

import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import com.jetbrains.python.inspections.quickfix.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PyUnusedLocalInspectionVisitor extends PyInspectionVisitor {
  private final boolean myIgnoreTupleUnpacking;
  private final boolean myIgnoreLambdaParameters;
  private final boolean myIgnoreRangeIterationVariables;
  private final boolean myIgnoreVariablesStartingWithUnderscore;

  // Names defined directly in a scope. They all belong to this scope.
  private final Map<ScopeOwner, @Unmodifiable Set<PsiElement>> myScopeWrites = new ConcurrentHashMap<>();
  // Names read directly in a scope. They might belong to some outer scope.
  private final Map<ScopeOwner, @Unmodifiable Set<PsiElement>> myScopeReads = new ConcurrentHashMap<>();

  public PyUnusedLocalInspectionVisitor(@NotNull ProblemsHolder holder,
                                        boolean ignoreTupleUnpacking,
                                        boolean ignoreLambdaParameters,
                                        boolean ignoreRangeIterationVariables,
                                        boolean ignoreVariablesStartingWithUnderscore,
                                        @NotNull TypeEvalContext context) {
    super(holder, context);
    myIgnoreTupleUnpacking = ignoreTupleUnpacking;
    myIgnoreLambdaParameters = ignoreLambdaParameters;
    myIgnoreRangeIterationVariables = ignoreRangeIterationVariables;
    myIgnoreVariablesStartingWithUnderscore = ignoreVariablesStartingWithUnderscore;
  }

  @Override
  public void visitPyElement(@NotNull PyElement node) {
    if (node instanceof ScopeOwner scopeOwner && !(node instanceof PyFile)) {
      processScope(scopeOwner);
    }
  }

  private void processScope(final ScopeOwner owner) {
    if (owner.getContainingFile() instanceof PyExpressionCodeFragment) {
      return;
    }
    if (owner instanceof PyFunction pyFunction && PyiUtil.isOverload(pyFunction, myTypeEvalContext)) {
      return;
    }
    if (!(owner instanceof PyClass) && !callsLocals(owner)) {
      collectAllWrites(owner);
    }
    owner.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyElement(@NotNull PyElement node) {
        if (node instanceof ScopeOwner scopeOwner) {
          collectUsedReads(scopeOwner);
        }
        super.visitPyElement(node);
      }
    });
  }

  private void collectAllWrites(ScopeOwner owner) {
    Set<PsiElement> scopeWrites = new HashSet<>();
    // type parameter list is not included in CFG
    if (owner instanceof PyTypeParameterListOwner typeParameterListOwner) {
      PyTypeParameterList typeParameterList = typeParameterListOwner.getTypeParameterList();
      if (typeParameterList != null) {
        scopeWrites.addAll(typeParameterList.getTypeParameters());
      }
    }
    final Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
    for (Instruction instruction : instructions) {
      final PsiElement element = instruction.getElement();
      if (element instanceof PyFunction && owner instanceof PyFunction) {
        if (PyKnownDecoratorUtil.hasUnknownDecorator((PyFunction)element, myTypeEvalContext)) {
          continue;
        }
        scopeWrites.add(element);
      }
      else if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
        final ReadWriteInstruction.ACCESS access = readWriteInstruction.getAccess();
        if (!access.isWriteAccess()) {
          continue;
        }
        final String name = readWriteInstruction.getName();
        // Ignore empty, wildcards, global and nonlocal names
        final Scope scope = ControlFlowCache.getScope(owner);
        if (name == null || PyNames.UNDERSCORE.equals(name) || scope.isGlobal(name) || scope.isNonlocal(name)) {
          continue;
        }
        if (element instanceof PyTargetExpression && ((PyTargetExpression)element).isQualified()) {
          continue;
        }
        // Ignore underscore-prefixed parameters
        if (name.startsWith(PyNames.UNDERSCORE) && element instanceof PyParameter) {
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
        if (PyTypeDeclarationStatementNavigator.isTypeDeclarationTarget(element)) {
          continue;
        }
        scopeWrites.add(element);
      }
    }
    myScopeWrites.put(owner, Collections.unmodifiableSet(scopeWrites));
  }

  private static boolean parameterInMethodWithFixedSignature(@NotNull ScopeOwner owner, @NotNull PsiElement element) {
    if (owner instanceof PyFunction function && element instanceof PyParameter) {
      final String functionName = function.getName();

      final LanguageLevel level = LanguageLevel.forElement(function);
      final Map<String, PyNames.BuiltinDescription> builtinMethods =
        function.getContainingClass() != null ? PyNames.getBuiltinMethods(level) : PyNames.getModuleBuiltinMethods(level);

      return functionName != null && !PyNames.INIT.equals(functionName) && builtinMethods.containsKey(functionName);
    }

    return false;
  }

  private @NotNull Set<PsiElement> analyzeReadsInDoctests(@NotNull PyStringLiteralExpression docstring, @NotNull ScopeOwner owner) {
    final PsiElement instrAnchor = PsiTreeUtil.getParentOfType(docstring, PyStatement.class);
    if (instrAnchor == null) return Collections.emptySet();
    final Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
    final int startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, instrAnchor);
    if (startInstruction < 0) return Collections.emptySet();
    final Project project = docstring.getProject();
    final List<Pair<PsiElement, TextRange>> pairs = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(docstring);
    if (pairs != null) {
      Set<PsiElement> result = new HashSet<>();
      for (Pair<PsiElement, TextRange> pair : pairs) {
        pair.getFirst().accept(new PyRecursiveElementVisitor() {
          @Override
          public void visitPyReferenceExpression(@NotNull PyReferenceExpression expr) {
            final PyExpression qualifier = expr.getQualifier();
            if (qualifier != null) {
              qualifier.accept(this);
              return;
            }
            final String name = expr.getName();
            if (name != null) {
              result.addAll(analyzeReadsInScope(name, owner, instructions, startInstruction, docstring));
            }
          }
        });
      }
      return result;
    }
    return Collections.emptySet();
  }


  private void collectUsedReads(final ScopeOwner owner) {
    // Avoid performing the analysis twice for the same nested function
    if (myScopeReads.containsKey(owner)) {
      return;
    }
    Set<PsiElement> allPathsScopeReads = new HashSet<>();
    if (owner instanceof PyDocStringOwner docStringOwner) {
      PyStringLiteralExpression docstring = docStringOwner.getDocStringExpression();
      if (docstring != null) {
        allPathsScopeReads.addAll(analyzeReadsInDoctests(docstring, owner));
      }
    }
    
    final Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
    for (int i = 0; i < instructions.length; i++) {
      final Instruction instruction = instructions[i];
      if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
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
        allPathsScopeReads.addAll(analyzeReadsInScope(name, owner, instructions, startInstruction, PyUtil.as(element, PyReferenceExpression.class)));
      }
    }
    myScopeReads.put(owner, Collections.unmodifiableSet(allPathsScopeReads));
  }

  private static @NotNull Set<PsiElement> analyzeReadsInScope(@NotNull String name,
                                                              @NotNull ScopeOwner owner,
                                                              Instruction @NotNull [] instructions,
                                                              int startInstruction,
                                                              @Nullable PsiElement scopeAnchor) {
    Set<PsiElement> readsFromInstruction = new HashSet<>();
    // Check if the element is declared out of scope, mark all out of scope write accesses as used
    if (scopeAnchor != null) {
      final ScopeOwner declOwner = ScopeUtil.getDeclarationScopeOwner(scopeAnchor, name);
      if (declOwner != null && declOwner != owner) {
        readsFromInstruction.addAll(ScopeUtil.getElementsOfAccessType(name, declOwner, ReadWriteInstruction.ACCESS.WRITE));
      }
    }
    ControlFlowUtil.iteratePrev(startInstruction, instructions, inst -> {
      final PsiElement instElement = inst.getElement();
      // Mark function as used
      if (instElement instanceof PyFunction) {
        if (name.equals(((PyFunction)instElement).getName())) {
          readsFromInstruction.add(instElement);
          return ControlFlowUtil.Operation.CONTINUE;
        }
      }
      // Mark write access as used
      else if (inst instanceof ReadWriteInstruction rwInstruction) {
        if (rwInstruction.getAccess().isWriteAccess() && name.equals(rwInstruction.getName())) {
          // Look up higher in CFG for actual definitions
          if (instElement != null && PyTypeDeclarationStatementNavigator.isTypeDeclarationTarget(instElement)) {
            return ControlFlowUtil.Operation.NEXT;
          }
          // For elements in scope
          if (instElement != null && PsiTreeUtil.isAncestor(owner, instElement, false)) {
            readsFromInstruction.add(instElement);
          }
          return ControlFlowUtil.Operation.CONTINUE;
        }
      }
      return ControlFlowUtil.Operation.NEXT;
    });
    return readsFromInstruction;
  }

  static class DontPerformException extends RuntimeException {}

  private static boolean callsLocals(final ScopeOwner owner) {
    try {
      owner.acceptChildren(new PyRecursiveElementVisitor(){
        @Override
        public void visitPyCallExpression(final @NotNull PyCallExpression node) {
          final PyExpression callee = node.getCallee();
          if (callee != null && "locals".equals(callee.getName())){
            throw new DontPerformException();
          }
          node.acceptChildren(this); // look at call expr in arguments
        }

        @Override
        public void visitPyFunction(final @NotNull PyFunction node) {
          // stop here
        }
      });
    }
    catch (DontPerformException e) {
      return true;
    }
    return false;
  }

  public void registerProblems() {
    final List<PyInspectionExtension> filters = PyInspectionExtension.EP_NAME.getExtensionList();
    // Register problems

    final Set<PyFunction> functionsWithInheritors = new HashSet<>();
    final Map<PyFunction, Boolean> emptyFunctions = new HashMap<>();

    Set<PsiElement> unusedElements = StreamEx.of(myScopeWrites.entrySet())
      .flatCollection(writeEntry -> ContainerUtil.subtract(writeEntry.getValue(), getReadsInsideScope(writeEntry.getKey())))
      .toImmutableSet();

    for (PsiElement element : unusedElements) {
      boolean ignoreUnused = false;
      for (PyInspectionExtension filter : filters) {
        if (filter.ignoreUnused(element, myTypeEvalContext)) {
          ignoreUnused = true;
        }
      }
      if (ignoreUnused) continue;

      if (element instanceof PyFunction) {
        // Local function
        final PsiElement nameIdentifier = ((PyFunction)element).getNameIdentifier();
        registerWarning(nameIdentifier == null ? element : nameIdentifier,
                        PyPsiBundle.message("INSP.unused.locals.local.function.isnot.used",
                                            ((PyFunction)element).getName()), new PyRemoveStatementQuickFix());
      }
      else if (element instanceof PyClass cls) {
        // Local class
        final PsiElement name = cls.getNameIdentifier();
        registerWarning(name != null ? name : element,
                        PyPsiBundle.message("INSP.unused.locals.local.class.isnot.used", cls.getName()), new PyRemoveStatementQuickFix());
      }
      else if (element instanceof PyTypeAliasStatement typeAlias) {
        final PsiElement name = typeAlias.getNameIdentifier();
        registerWarning(name != null ? name : element,
                        PyPsiBundle.message("INSP.unused.locals.type.alias.isnot.used", typeAlias.getName()),
                        new PyRemoveStatementQuickFix());
      }
      else if (element instanceof PyTypeParameter typeParameter) {
        final PsiElement name = typeParameter.getNameIdentifier();
        registerWarning(name != null ? name : element,
                        PyPsiBundle.message("INSP.unused.locals.type.parameter.isnot.used", typeParameter.getName()),
                        new PyRemoveStatementQuickFix());
      }
      else {
        // Local variable or parameter
        String name = element instanceof PsiNamedElement namedElement ? StringUtil.notNullize(namedElement.getName()) : element.getText();
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
          if (paramList != null && paramList.getParent() instanceof PyFunction func) {
            containingClass = func.getContainingClass();
            if (containingClass != null &&
                PyUtil.isInitMethod(func) &&
                !namedParameter.isKeywordContainer() &&
                !namedParameter.isPositionalContainer()) {
              mayBeField = true;
            }
            else if (ignoreUnusedParameters(func, functionsWithInheritors)) {
              continue;
            }
            if (func.asMethod() != null) {
              Boolean isEmpty = emptyFunctions.get(func);
              if (isEmpty == null) {
                isEmpty = PyUtil.isEmptyFunction(func);
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
          registerWarning(element, PyPsiBundle.message("INSP.unused.locals.parameter.isnot.used", name), fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
        else {
          if (myIgnoreVariablesStartingWithUnderscore && element.getText().startsWith(PyNames.UNDERSCORE)) continue;
          if (myIgnoreTupleUnpacking && isTupleUnpacking(element, unusedElements)) continue;

          final String warningMsg = PyPsiBundle.message("INSP.unused.locals.local.variable.isnot.used", name);

          final PyForStatement forStatement = PyForStatementNavigator.getPyForStatementByIterable(element);
          if (forStatement != null) {
            if (!myIgnoreRangeIterationVariables || !isRangeIteration(forStatement)) {
              registerWarning(element, warningMsg, new ReplaceWithWildCard());
            }
            continue;
          }

          if (isComprehensionTarget(element)) {
            registerWarning(element, warningMsg, new ReplaceWithWildCard());
            continue;
          }

          final PyExceptPart exceptPart = PyExceptPartNavigator.getPyExceptPartByTarget(element);
          if (exceptPart != null) {
            registerWarning(element, warningMsg, new PyRemoveExceptionTargetQuickFix());
            continue;
          }

          final PyWithItem withItem = PsiTreeUtil.getParentOfType(element, PyWithItem.class);
          if (withItem != null && PsiTreeUtil.isAncestor(withItem.getTarget(), element, false)) {
            if (withItem.getTarget() == element) {
              registerWarning(element, warningMsg, new PyRemoveWithTargetQuickFix());
            }
            else {
              registerWarning(element, warningMsg, new ReplaceWithWildCard());
            }
            continue;
          }

          final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
          if (assignmentStatement != null && !PsiTreeUtil.isAncestor(assignmentStatement.getAssignedValue(), element, false)) {
            if (assignmentStatement.getLeftHandSideExpression() == element) {
              // Single assignment target (unused = value)
              registerWarning(element, warningMsg, new PyRemoveAssignmentStatementTargetQuickFix(), new PyRemoveStatementQuickFix());
            }
            else if (ArrayUtil.contains(element, assignmentStatement.getRawTargets())) {
              // Chained assignment target (used = unused = value)
              registerWarning(element, warningMsg, new PyRemoveAssignmentStatementTargetQuickFix());
            }
            else {
              // Unpacking (used, unused = value)
              registerWarning(element, warningMsg, new ReplaceWithWildCard());
            }
            continue;
          }

          registerWarning(element, warningMsg);
        }
      }
    }
  }

  private @NotNull Set<PsiElement> getReadsInsideScope(@NotNull ScopeOwner scopeOwner) {
    return StreamEx.of(myScopeReads.entrySet())
      .filter(readEntry -> PsiTreeUtil.isAncestor(scopeOwner, readEntry.getKey(), false))
      .flatCollection(readEntry -> readEntry.getValue())
      .toImmutableSet();
  }

  private static boolean isComprehensionTarget(@NotNull PsiElement element) {
    final PyComprehensionElement comprehensionExpr = PsiTreeUtil.getParentOfType(element, PyComprehensionElement.class);
    if (comprehensionExpr == null) return false;
    return ContainerUtil.exists(comprehensionExpr.getForComponents(),
                                it -> PsiTreeUtil.isAncestor(it.getIteratorVariable(), element, false));
  }

  private boolean isRangeIteration(@NotNull PyForStatement forStatement) {
    final PyExpression source = forStatement.getForPart().getSource();
    if (!(source instanceof PyCallExpression expr)) {
      return false;
    }
    if (expr.isCalleeText("range", "xrange")) {
      final PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);
      final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(forStatement);

      return ContainerUtil.exists(expr.multiResolveCalleeFunction(resolveContext), builtinCache::isBuiltin);
    }
    return false;
  }

  private boolean ignoreUnusedParameters(PyFunction func, Set<PyFunction> functionsWithInheritors) {
    if (functionsWithInheritors.contains(func)) {
      return true;
    }
    if (!PyUtil.isInitMethod(func) && PySuperMethodsSearch.search(func, myTypeEvalContext).findFirst() != null ||
        PyOverridingMethodsSearch.search(func, true).findFirst() != null) {
      functionsWithInheritors.add(func);
      return true;
    }
    return false;
  }

  private static boolean isTupleUnpacking(PsiElement element, Set<PsiElement> unusedElements) {
    if (!(element instanceof PyTargetExpression)) {
      return false;
    }
    // Handling of the star expressions
    PsiElement parent = element.getParent();
    if (parent instanceof PyStarExpression){
      element = parent;
      parent = element.getParent();
    }
    if (parent instanceof PyTupleExpression tuple) {
      // if all the items of the tuple are unused, we still highlight all of them; if some are unused, we ignore
      for (PyExpression expression : tuple.getElements()) {
        if (expression instanceof PyStarExpression){
          if (!unusedElements.contains(((PyStarExpression)expression).getExpression())) {
            return true;
          }
        }
        else if (!unusedElements.contains(expression)) {
          return true;
        }
      }
    }
    return false;
  }

  private void registerWarning(@NotNull PsiElement element, @InspectionMessage String msg, @NotNull LocalQuickFix @NotNull... quickfixes) {
    registerProblem(element, msg, ProblemHighlightType.LIKE_UNUSED_SYMBOL, null, quickfixes);
  }

  private static class ReplaceWithWildCard extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return PyPsiBundle.message("INSP.unused.locals.replace.with.wildcard");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PyFile pyFile = (PyFile) PyElementGenerator.getInstance(element.getProject()).createDummyFile(LanguageLevel.getDefault(),
                                                                                                             "for _ in tuples:\n  pass"
      );
      final PyExpression target = ((PyForStatement)pyFile.getStatements().get(0)).getForPart().getTarget();
      if (target != null) {
        element.replace(target);
      }
    }
  }
}
