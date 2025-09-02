// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import static com.jetbrains.python.PyNames.BUILTINS_MODULES;

public class PySmartStepIntoVariantVisitor extends PyRecursiveElementVisitor {
  private static final String RERAISE = "RERAISE";

  int myVariantIndex = -1;
  private final @NotNull List<PySmartStepIntoVariant> myCollector;
  private final @NotNull List<Pair<String, Boolean>> myVariantsFromPython;
  private final @NotNull PySmartStepIntoContext myContext;
  private final @NotNull Map<String, Integer> mySeenVariants = Maps.newHashMap();
  private final @NotNull Set<PsiElement> alreadyVisited = new HashSet<>();

  public PySmartStepIntoVariantVisitor(@NotNull List<PySmartStepIntoVariant> collector,
                                       @NotNull List<Pair<String, Boolean>> variantsFromPython,
                                       @NotNull PySmartStepIntoContext context) {
    myCollector = collector;
    boolean shouldJump = false;
    int mid = (variantsFromPython.size() - 1) / 2;
    if (variantsFromPython.get(variantsFromPython.size() - 1).first.equals(RERAISE)) {
      int i = 0;
      while (i < mid) {
        if (!variantsFromPython.get(i).first.equals(variantsFromPython.get(mid + i).first) ||
            !(variantsFromPython.get(i).second && !variantsFromPython.get(mid + i).second)) {
          break;
        }
        i++;
      }
      if (i == mid) {
        shouldJump = true;
      }
    }

    if (shouldJump) {
      myVariantsFromPython = variantsFromPython.subList(mid, variantsFromPython.size() - 1);
    }
    else if (variantsFromPython.get(variantsFromPython.size() - 1).first.equals(RERAISE)) {
      myVariantsFromPython = variantsFromPython.subList(0, variantsFromPython.size() - 1);
    }
    else {
      myVariantsFromPython = variantsFromPython;
    }

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

    myVariantIndex++;
    int callOrder = getCallOrder();
    mySeenVariants.put(myVariantsFromPython.get(myVariantIndex).first, ++callOrder);

    PsiElement ref = callee.getReference() != null ? callee.getReference().resolve() : null;
    if (ref != null && isBuiltIn(ref)) return;

    if (LanguageLevel.forElement(node).isOlderThan(LanguageLevel.PYTHON312)
        && ref instanceof PyFunction && ((((PyFunction)ref).isAsync()) || ((PyFunction)ref).isGenerator())) {
      return;
    }

    if (isAlreadyCalled()) return;

    myCollector.add(new PySmartStepIntoVariantCallExpression(node, callOrder, myContext));
  }

  @Override
  public void visitPyDecoratorList(@NotNull PyDecoratorList node) {
    PyDecorator[] decorators = node.getDecorators();
    if (decorators.length > 0) {
      decorators[0].accept(this);
    }
  }

  @Override
  public void visitPyComprehensionElement(@NotNull PyComprehensionElement node) {
    node.acceptChildren(this);

    if (alreadyVisited.contains(node)) return;
    alreadyVisited.add(node);

    if (myVariantIndex == myVariantsFromPython.size() - 1) return;

    if (!PySmartStepIntoVariantComprehension.isComprehensionName(myVariantsFromPython.get(myVariantIndex + 1).first)) return;

    myVariantIndex++;
    int callOrder = getCallOrder();
    mySeenVariants.put(myVariantsFromPython.get(myVariantIndex).first, ++callOrder);

    if (isAlreadyCalled()) return;

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

    var context = TypeEvalContext.userInitiated(expression.getProject(), expression.getContainingFile());
    PsiElement resolved = expression.getReference(PyResolveContext.defaultContext(context)).resolve();

    if (resolved == null || isBuiltIn(resolved) || isAlreadyCalled()) return;

    PsiElement psiOperator = isBinaryOperator ? ((PyBinaryExpression)expression).getPsiOperator() : expression;
    if (psiOperator != null) myCollector.add(new PySmartStepIntoVariantOperator(psiOperator, callOrder, myContext));
  }

  private static boolean isBuiltIn(@NotNull PsiElement ref) {
    PsiElement navFile = ref.getNavigationElement().getContainingFile();
    return (navFile instanceof PyFile && BUILTINS_MODULES.contains(navFile.getContainingFile().getName())
            || navFile instanceof PyiFile);
  }

  private boolean isAlreadyCalled() {
    return myVariantsFromPython.get(myVariantIndex).second;
  }

  private int getCallOrder() {
    return mySeenVariants.getOrDefault(myVariantsFromPython.get(myVariantIndex).first, -1);
  }
}
