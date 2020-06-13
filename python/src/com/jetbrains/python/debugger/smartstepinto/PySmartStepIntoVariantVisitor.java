// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.smartstepinto;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PySmartStepIntoVariantVisitor extends PyRecursiveElementVisitor {
  @NotNull @NonNls private static final ImmutableSet<String> BUILTINS_MODULES = ImmutableSet.of("builtins.py", "__builtin__.py");

  int myVariantIndex = -1;
  @NotNull private final List<PySmartStepIntoVariant> myCollector;
  @NotNull private final List<Pair<String, Boolean>> myVariantsFromPython;
  @NotNull private final PySmartStepIntoContext myContext;
  @NotNull private final Map<String, Integer> mySeenVariants = Maps.newHashMap();
  @NotNull private final Set<PsiElement> alreadyVisited = new HashSet<PsiElement>();

  public PySmartStepIntoVariantVisitor(@NotNull List<PySmartStepIntoVariant> collector,
                                       @NotNull List<Pair<String, Boolean>> variantsFromPython,
                                       @NotNull PySmartStepIntoContext context) {
    myCollector = collector;
    myVariantsFromPython = variantsFromPython;
    myContext = context;
  }

  @Override
  public void visitPyCallExpression(@NotNull PyCallExpression node) {
    node.acceptChildren(this);

    if (alreadyVisited.contains(node)) return;
    alreadyVisited.add(node);

    if (myVariantIndex == myVariantsFromPython.size() - 1) return;

    PyExpression callee = node.getCallee();
    if (callee == null || callee.getName() == null) return;

    if (!callee.getName().equals(myVariantsFromPython.get(myVariantIndex + 1).first)) return;

    myVariantIndex++;
    int callOrder = getCallOrder();
    mySeenVariants.put(myVariantsFromPython.get(myVariantIndex).first, ++callOrder);

    PsiElement ref = callee.getReference() != null ? callee.getReference().resolve() : null;
    if (ref != null && isBuiltIn(ref)) return;

    if (ref instanceof PyFunction && ((((PyFunction)ref).isAsync()) || ((PyFunction)ref).isGenerator())) return;

    if (isAlreadySeen()) return;

    myCollector.add(new PySmartStepIntoVariantCallExpression(node, callOrder, myContext));
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (element instanceof PyDecorator) {
      visitPyCallExpression((PyDecorator)element);
    }
    super.visitElement(element);
  }

  @Override
  public void visitPyDecoratorList(@NotNull PyDecoratorList node) {
    PyDecorator[] decorators = node.getDecorators();
    if (decorators.length > 0) {
      decorators[0].accept(this);
      visitPyCallExpression(decorators[0]);
    }
  }

  @Override
  public void visitPyComprehensionElement(PyComprehensionElement node) {
    node.acceptChildren(this);

    if (alreadyVisited.contains(node)) return;
    alreadyVisited.add(node);

    if (myVariantIndex == myVariantsFromPython.size() - 1) return;

    if (!PySmartStepIntoVariantComprehension.isComprehensionName(myVariantsFromPython.get(myVariantIndex + 1).first)) return;

    myVariantIndex++;
    int callOrder = getCallOrder();
    mySeenVariants.put(myVariantsFromPython.get(myVariantIndex).first, ++callOrder);

    if (isAlreadySeen()) return;

    myCollector.add(new PySmartStepIntoVariantComprehension(node, callOrder, myContext));
  }

  @Override
  public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
    // We have to visit both branches of a binary expression first to preserve the execution order as it is in a CPython interpreter,
    // E.g. in the `f() + g()` expression the execution order goes like `f()`, `g()`, `+`, but it's `f()`, `+`, `g()` in the PSI tree.
    node.getLeftExpression().accept(this);
    if (node.getRightExpression() != null) node.getRightExpression().accept(this);
    node.acceptChildren(this);

    if (alreadyVisited.contains(node)) return;
    alreadyVisited.add(node);

    if (myVariantIndex == myVariantsFromPython.size() - 1) return;

    PyElementType operator = node.getOperator();

    if (PyTokenTypes.OPERATIONS.contains(operator)) processOperator(operator, node);
  }

  @Override
  public void visitPyPrefixExpression(@NotNull PyPrefixExpression node) {
    PyExpression operand = node.getOperand();
    if (operand != null) operand.accept(this);

    PyElementType operator = node.getOperator();

    processOperator(operator, node);
  }

  private void processOperator(PyElementType operator, PyReferenceOwner expression) {
    boolean isBinaryOperator = expression instanceof PyBinaryExpression;

    String specialMethodName = isBinaryOperator ? operator.getSpecialMethodName() :
                               PySmartStepIntoVariantOperator.getUnaryOperatorSpecialMethodName(operator);

    if (specialMethodName == null || !specialMethodName.equals(myVariantsFromPython.get(myVariantIndex + 1).first)) return;
    myVariantIndex++;

    int callOrder = getCallOrder();
    mySeenVariants.put(myVariantsFromPython.get(myVariantIndex).first, ++callOrder);

    PsiElement resolved = expression.getReference(
      PyResolveContext.defaultContext().withTypeEvalContext(TypeEvalContext.userInitiated(
        expression.getProject(), expression.getContainingFile()))).resolve();

    if (resolved == null || isBuiltIn(resolved) || isAlreadySeen()) return;

    PsiElement psiOperator = isBinaryOperator ? ((PyBinaryExpression)expression).getPsiOperator() : expression;
    if (psiOperator != null) myCollector.add(new PySmartStepIntoVariantOperator(psiOperator, callOrder, myContext));
  }

  private static boolean isBuiltIn(@NotNull PsiElement ref) {
    PsiElement navFile = ref.getNavigationElement().getContainingFile();
    return (navFile instanceof PyFile && BUILTINS_MODULES.contains(navFile.getContainingFile().getName())
            || navFile instanceof PyiFile);
  }

  private boolean isAlreadySeen() {
    return myVariantsFromPython.get(myVariantIndex).second;
  }

  private int getCallOrder() {
    return mySeenVariants.getOrDefault(myVariantsFromPython.get(myVariantIndex).first, -1);
  }
}
