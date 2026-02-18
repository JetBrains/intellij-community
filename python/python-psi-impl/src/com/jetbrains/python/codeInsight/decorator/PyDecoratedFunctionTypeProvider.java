package com.jetbrains.python.codeInsight.decorator;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecoratable;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyCallableTypeImpl;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PySyntheticCallHelper;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.resolve.PyResolveUtil.resolveQualifiedNameInScope;
import static com.jetbrains.python.psi.types.PyTypeUtil.notNullToRef;

/**
 * Infer type for reference of decorated {@link PyDecoratable} objects.
 */
public final class PyDecoratedFunctionTypeProvider extends PyTypeProviderBase {

  @Override
  public @Nullable Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget,
                                                @NotNull TypeEvalContext context,
                                                @Nullable PsiElement anchor) {
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
    return evaluateType(pyDecoratable, context, explicitlyTypedDecorators);
  }

  private static boolean isTransparentDecorator(@NotNull PyDecorator decorator, @NotNull TypeEvalContext context) {
    return !PyKnownDecoratorUtil.asKnownDecorators(decorator, context).isEmpty() || isUntypedDecorator(decorator, context);
  }

  private static boolean isUntypedDecorator(@NotNull PyDecorator decorator, @NotNull TypeEvalContext context) {
    QualifiedName qualifiedName = decorator.getQualifiedName();
    if (qualifiedName == null) return false;
    // Decorator is the only expression persisted in PSI stubs.
    // Calling decorator.getCallee() to retrieve a reference will cause un-stubbing of the containing file
    List<PsiElement> resolved = resolveQualifiedNameInScope(qualifiedName, (PyFile)decorator.getContainingFile(), context);
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
    @Nullable PyCallableType currentType = null;

    for (int i = decorators.size() - 1; i >= 0; i--) {
      PyDecorator decorator = decorators.get(i);
      PyType argument;
      if (currentType != null) {
        argument = currentType;
      }
      else {
        argument = context.getType((PyTypedElement)referenceTarget);
      }

      PyCallableType decoratorType = getDecoratorType(decorator, argument, context);
      if (decoratorType != null) {
        PyType newType = PySyntheticCallHelper.getCallTypeOnTypesOnly(
          decoratorType,
          null,
          Collections.singletonList(argument),
          context
        );

        if (newType instanceof PyCallableType newFunctionType) {
          currentType = newFunctionType;
        }
      }
    }

    if (currentType instanceof PyCallableTypeImpl && referenceTarget instanceof PyFunction callableReference) {
      return Ref.create(new PyCallableTypeImpl(
        currentType.getParameters(context),
        currentType.getReturnType(context),
        callableReference,
        currentType.getModifier(),
        currentType.getImplicitOffset()
      ));
    }

    // TODO Don't ignore explicit return type Any on one of the decorators
    return notNullToRef(currentType);
  }

  private static @Nullable PyCallableType getDecoratorType(@NotNull PyDecorator decorator,
                                                           @Nullable PyType decoratedFunctionType,
                                                           @NotNull TypeEvalContext context) {
    QualifiedName decoratorQualifiedName = decorator.getQualifiedName();
    if (decoratorQualifiedName == null) return null;
    PsiFile file = decorator.getContainingFile();
    PyTypedElement typedElement = StreamEx.of(resolveQualifiedNameInScope(decoratorQualifiedName, (PyFile)file, context))
      .select(PyTypedElement.class)
      .findFirst()
      .orElse(null);
    if (typedElement != null) {
      if (decorator.hasArgumentList()) {
        return getDecoratorCallType(decorator, typedElement, context);
      }

      List<PyFunction> overloads;
      if (typedElement instanceof PyFunction resolvedFunction) {
        overloads = PyiUtil.getOverloads(resolvedFunction, context);
      }
      else {
        overloads = Collections.emptyList();
      }
      return getDecoratorCalleeType(typedElement, Collections.singletonList(decoratedFunctionType), overloads, context);
    }

    return null;
  }

  private static @Nullable PyCallableType getDecoratorCallType(@NotNull PyDecorator decorator,
                                                               @NotNull PyTypedElement typedElement,
                                                               @NotNull TypeEvalContext context) {
    PyType typedElementType = context.getType(typedElement);
    List<PyFunction> overloads = Collections.emptyList();
    if (typedElement instanceof PyFunction resolvedFunction) {
      overloads = PyiUtil.getOverloads(resolvedFunction, context);
    }


    // Shortcircuit:
    // if there are no overloads and the decorator is not generic,
    // there's no need to `PySyntheticCallHelper.getCallTypeOnTypesOnly` to get its return type
    // and a simple `getReturnType` can be used instead
    if (typedElementType instanceof PyCallableType callableType &&
        !PyTypeChecker.hasGenerics(typedElementType, context) &&
        overloads.isEmpty()) {
      PyType returnType = callableType.getReturnType(context);
      if (returnType instanceof PyCallableType returnPyCallableType) {
        return returnPyCallableType;
      }
      else {
        return null;
      }
    }


    // Otherwise:
    // if the decorator has overloads or is generic, we have to calculate its type
    if (context.maySwitchToAST(decorator)) {
      PyType type = context.getType(decorator);
      if (type instanceof PyCallableType callableType) {
        return callableType;
      }
    }

    // If we cannot switch to AST, our only option is to calculate the call with no arguments
    // TODO: instead, we should return an UnsafeUnion of all possible overloads
    PyCallableType decoratorCalleeType = getDecoratorCalleeType(typedElement, List.of(), overloads, context);
    if (decoratorCalleeType != null) {
      PyType newType = PySyntheticCallHelper.getCallTypeOnTypesOnly(
        decoratorCalleeType,
        null,
        List.of(),
        context
      );

      if (newType instanceof PyCallableType newFunctionType) {
        return newFunctionType;
      }
    }

    return null;
  }

  private static @Nullable PyCallableType getDecoratorCalleeType(@NotNull PyTypedElement typedElement,
                                                                 @NotNull List<@Nullable PyType> decoratorArgumentTypes,
                                                                 @NotNull List<PyFunction> overloads,
                                                                 @NotNull TypeEvalContext context) {
    List<? extends PyTypedElement> resolvedElements;
    if (typedElement instanceof PyFunction resolvedFunction) {
      if (overloads.isEmpty()) {
        resolvedElements = List.of(resolvedFunction);
      }
      else {
        resolvedElements = PySyntheticCallHelper.matchOverloadsByArgumentTypes(overloads, decoratorArgumentTypes, null, context);
      }
    }
    else {
      resolvedElements = List.of(typedElement);
    }

    if (resolvedElements.size() == 1) {
      PyTypedElement resolvedElement = resolvedElements.getFirst();
      PyType type;
      if (resolvedElement instanceof PyClass pyClass) {
        type = PyTypeChecker.findGenericDefinitionType(pyClass, context);
        if (type instanceof PyClassType classType) {
          type = classType.toClass();
        }
      }
      else {
        type = context.getType(resolvedElement);
      }
      if (type instanceof PyCallableType callableType) {
        return callableType;
      }
    }
    return null;
  }
}
