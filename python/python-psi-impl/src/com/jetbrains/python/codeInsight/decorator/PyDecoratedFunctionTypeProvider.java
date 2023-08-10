package com.jetbrains.python.codeInsight.decorator;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.psi.types.PyTypeUtil.notNullToRef;

/**
 * Infer type for reference of decorated {@link PyDecoratable} objects.
 */
public class PyDecoratedFunctionTypeProvider extends PyTypeProviderBase {

  @Nullable
  @Override
  public Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    if (!(referenceTarget instanceof PyDecoratable pyDecoratable)) {
      return null;
    }
    PyDecoratorList decoratorList = pyDecoratable.getDecoratorList();
    if (decoratorList == null) {
      return null;
    }
    List<PyDecorator> decorators = ContainerUtil.filter(decoratorList.getDecorators(), d ->
      PyKnownDecoratorUtil.asKnownDecorators(d, context).isEmpty()
    );
    if (decorators.isEmpty()) {
      return null;
    }

    /* Our goal is to infer the type of reference of decorated object.
     * For that we going to infer a type of expression <code>decorator(reference)<code>.
     * The expression contains a new reference for the same object, and our
     * type inferring engine will ask this provider about the type of the object again.
     * To prevent an infinite loop, we need to add a recursion guard here. */
    return RecursionManager.doPreventingRecursion(
      Pair.create(referenceTarget, context),
      false,
      () -> evaluateType(pyDecoratable, context, decorators)
    );
  }

  @Nullable
  private static Ref<PyType> evaluateType(@NotNull PyDecoratable referenceTarget,
                                          @NotNull TypeEvalContext context,
                                          @NotNull List<PyDecorator> decorators) {
    PyExpression fakeCallExpression = fakeCallExpression(referenceTarget, decorators, context);
    if (fakeCallExpression == null) {
      return null;
    }
    return notNullToRef(context.getType(fakeCallExpression));
  }

  @Nullable
  private static PyExpression fakeCallExpression(@NotNull PyDecoratable referenceTarget,
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
    result.append(referenceTarget.getName());
    StringUtil.repeatSymbol(result, ')', decorators.size());

    return PyUtil.createExpressionFromFragment(result.toString(), referenceTarget.getContainingFile());
  }
}
