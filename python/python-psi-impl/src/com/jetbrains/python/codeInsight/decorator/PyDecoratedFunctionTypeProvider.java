package com.jetbrains.python.codeInsight.decorator;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.psi.types.PyTypeUtil.notNullToRef;

/**
 * Infer type for reference of decorated {@link PyDecoratable} objects.
 */
public final class PyDecoratedFunctionTypeProvider extends PyTypeProviderBase {

  @Override
  public @Nullable Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    if (!(referenceTarget instanceof PyDecoratable pyDecoratable)) {
      return null;
    }
    PyDecoratorList decoratorList = pyDecoratable.getDecoratorList();
    if (decoratorList == null) {
      return null;
    }

    PyDecorator[] decorators = decoratorList.getDecorators();
    List<PyDecorator> explicitlyTypedDecorators = ContainerUtil.filter(decorators, d -> !isTransparentDecorator(d, context));
    if (explicitlyTypedDecorators.isEmpty()) {
      return null;
    }

    /* Our goal is to infer the type of reference of a decorated object.
     * For that we are going to infer a type of expression <code>decorator(reference)<code>.
     * The expression contains a new reference for the same object, and our
     * type inferring engine will ask this provider about the type of the object again.
     * To prevent an infinite loop, we need to add a recursion guard here. */
    return RecursionManager.doPreventingRecursion(
      Pair.create(referenceTarget, context),
      false,
      () -> evaluateType(pyDecoratable, context, explicitlyTypedDecorators)
    );
  }

  private static boolean isTransparentDecorator(@NotNull PyDecorator decorator, @NotNull TypeEvalContext context) {
    return !PyKnownDecoratorUtil.asKnownDecorators(decorator, context).isEmpty() || isUntypedDecorator(decorator, context);
  }

  private static boolean isUntypedDecorator(@NotNull PyDecorator decorator, @NotNull TypeEvalContext context) {
    QualifiedName qualifiedName = decorator.getQualifiedName();
    if (qualifiedName == null) return false;
    // Decorator is the only expression persisted in PSI stubs.
    // Calling decorator.getCallee() to retrieve a reference will cause un-stubbing of the containing file
    List<PsiElement> resolved = PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, (PyFile)decorator.getContainingFile(), context);
    for (PsiElement res : resolved) {
      if (res instanceof PyFunction function) {
        if (hasReturnTypeHint(function, context)) {
          return false;
        }
      }
      else if (res instanceof PyClass) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasReturnTypeHint(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    return StreamEx.of(function)
      .append(PyiUtil.getOverloads(function, context))
      .anyMatch(f -> f.getTypeCommentAnnotation() != null || f.getAnnotation() != null);
  }

  private static @Nullable Ref<PyType> evaluateType(@NotNull PyDecoratable referenceTarget,
                                                    @NotNull TypeEvalContext context,
                                                    @NotNull List<PyDecorator> decorators) {
    PyExpression fakeCallExpression = fakeCallExpression(referenceTarget, decorators, context);
    if (fakeCallExpression == null) {
      return null;
    }
    // TODO Don't ignore explicit return type Any on one of the decorators 
    return notNullToRef(context.getType(fakeCallExpression));
  }

  private static @Nullable PyExpression fakeCallExpression(@NotNull PyDecoratable referenceTarget,
                                                           @NotNull List<PyDecorator> decorators,
                                                           @NotNull TypeEvalContext context) {
    StringBuilder result = new StringBuilder();

    for (PyDecorator decorator : decorators) {
      if (context.maySwitchToAST(decorator)) {
        result.append(decorator.getText().substring(1));
      }
      else {
        result
          .append(decorator.getName())
          .append(decorator.hasArgumentList() ? "()" : "");
      }
      result.append("(");
    }
    if (ScopeUtil.getScopeOwner(referenceTarget) instanceof PyClass containingClass) {
      result.append(containingClass.getName()).append(".");
    }
    result.append(referenceTarget.getName());
    StringUtil.repeatSymbol(result, ')', decorators.size());

    return PyUtil.createExpressionFromFragment(result.toString(), referenceTarget.getContainingFile());
  }
}
