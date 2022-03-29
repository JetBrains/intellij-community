package com.jetbrains.python.codeInsight.decorator;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    List<PyKnownDecoratorUtil.KnownDecorator> filteredDecorators = new ArrayList<>();
    filteredDecorators.add(PyKnownDecoratorUtil.KnownDecorator.TYPING_OVERLOAD);
    filteredDecorators.add(PyKnownDecoratorUtil.KnownDecorator.STATICMETHOD);
    filteredDecorators.add(PyKnownDecoratorUtil.KnownDecorator.CLASSMETHOD);

    var decorators = Arrays.stream(decoratorList.getDecorators()).toList();
    var haveSpecialCase = ContainerUtil.exists(decorators, d ->
      ContainerUtil.exists(PyKnownDecoratorUtil.asKnownDecorators(d, context),
                           it -> ContainerUtil.exists(filteredDecorators, filtered -> filtered.equals(it))));
    if (haveSpecialCase) return null;


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
    PyType sourceType = null;
    if (referenceTarget instanceof PyTypedElement typedElement) {
      sourceType = context.getType(typedElement);
    }

    var annotatedDecorators = getAnnotatedDecorators(referenceTarget, decorators, context);
    if (!annotatedDecorators.isEmpty()) {
      PyExpression fakeCallExpression = fakeCallExpression(referenceTarget, annotatedDecorators, context);
      if (fakeCallExpression == null) {
        return null;
      }
      var fakeCallExpressionType = context.getType(fakeCallExpression);
      return notNullToRef(fakeCallExpressionType);
    }

    return notNullToRef(sourceType);
  }

  @NotNull
  private static List<PyDecorator> getAnnotatedDecorators(@NotNull PyDecoratable referenceTarget,
                                                          @NotNull List<PyDecorator> decorators,
                                                          @NotNull TypeEvalContext context) {
    var result = new ArrayList<PyDecorator>();
    var scopeOwner = ScopeUtil.getScopeOwner(referenceTarget);
    if (scopeOwner == null) return Collections.emptyList();
    for (var decorator : decorators) {
      var qualifiedName = decorator.getQualifiedName();
      if (qualifiedName == null) continue;
      var resolved = PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, scopeOwner, context);
      for (var res : resolved) {
        if (res instanceof PyFunction function) {
          var annotation = PyTypingTypeProvider.getReturnTypeAnnotation(function, context);
          if (annotation != null) {
            result.add(decorator);
          }
        }
        else if (res instanceof PyClass) {
          result.add(decorator);
        }
      }
    }
    return result;
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
