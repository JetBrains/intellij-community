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
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyCallableTypeImpl;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyCollectionType;
import com.jetbrains.python.psi.types.PyConcatenateType;
import com.jetbrains.python.psi.types.PyParamSpecType;
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
  public @Nullable Ref<PyType> getParameterType(@NotNull PyNamedParameter param,
                                                @NotNull PyFunction func,
                                                @NotNull TypeEvalContext context) {
    if (param.getAnnotation() != null || param.getTypeCommentAnnotation() != null) return null;
    if (param.isPositionalContainer() || param.isKeywordContainer()) return null;

    var decoratorList = func.getDecoratorList();
    if (decoratorList == null) return null;
    var decorators = decoratorList.getDecorators();

    // Walk from the innermost decorator (applied directly to `func`) outward and infer from the
    // first one that declares an explicit parameter signature. Unlike the reference type, parameter
    // inference only needs the decorator's parameter signature, not its return type, so a decorator
    // without a return-type hint (e.g. `def d(fn: Callable[[int], str]): ...`) is still usable.
    // Decorators without parameter types (untyped/identity decorators) are treated as identity and
    // skipped. Known decorators (@staticmethod, @classmethod, @property, ...) never constrain
    // `func`'s parameters and must not override inference for parameters such as `self`, so they
    // are skipped too.
    for (int i = decorators.length - 1; i >= 0; i--) {
      PyDecorator decorator = decorators[i];
      if (!PyKnownDecoratorUtil.asKnownDecorators(decorator, context).isEmpty()) continue;

      List<PyCallableParameter> expectedParams = getExpectedFunctionParameters(decorator, context);
      if (expectedParams == null) continue;

      // Find the position of param among the function's named parameters
      var params = ParamHelper.collectNamedParameters(func.getParameterList());
      int paramPos = params.indexOf(param);
      if (paramPos < 0) return null;

      var type = ParamHelper.getExpectedTypeForPositionalParam(paramPos, expectedParams, context);
      // A Concatenate/ParamSpec describes a whole parameter list, not a single parameter's type, so it must
      // not be assigned to one parameter - defer to regular inference.
      if (type instanceof PyConcatenateType || type instanceof PyParamSpecType) return null;

      // When the expected parameter type is a free type variable of a generic decorator, bind it from the
      // type the surrounding decorator chain expects of this decorator's result.
      if (type != null && PyTypeChecker.hasGenerics(type, context)) {
        PyType resolved = resolveGenericParamFromDecoratorChain(decorators, i, decorator, type, context);
        if (resolved != null) type = resolved;
      }
      return type != null ? Ref.create(type) : null;
    }
    return null;
  }

  /**
   * Binds {@code expectedParamType}'s type variables by matching the decorator's return type against the type
   * the surrounding decorators expect of its result. E.g. for {@code @d2 @d1 def f(i)} with
   * {@code d1(fn: Callable[[T], object]) -> T} and {@code d2(i: int)}, {@code T} binds to {@code int}.
   */
  private static @Nullable PyType resolveGenericParamFromDecoratorChain(PyDecorator @NotNull [] decorators,
                                                                        int decoratorIndex,
                                                                        @NotNull PyDecorator decorator,
                                                                        @NotNull PyType expectedParamType,
                                                                        @NotNull TypeEvalContext context) {
    PyType expectedResultType = expectedResultType(decorators, decoratorIndex, context);
    if (expectedResultType == null) return null;

    PyCallableType decoratorType = getDecoratorType(decorator, null, context);
    PyType decoratorReturnType = decoratorType != null ? decoratorType.getReturnType(context) : null;
    return bindGenerics(decoratorReturnType, expectedResultType, expectedParamType, context);
  }

  /**
   * The type the nearest non-transparent decorator wrapping {@code decorators[decoratorIndex]} expects of its
   * result, resolving a generic outer decorator recursively from what wraps it.
   */
  private static @Nullable PyType expectedResultType(PyDecorator @NotNull [] decorators,
                                                     int decoratorIndex,
                                                     @NotNull TypeEvalContext context) {
    for (int j = decoratorIndex - 1; j >= 0; j--) {
      if (isTransparentDecorator(decorators[j], context)) continue;

      PyCallableType outerType = getDecoratorType(decorators[j], null, context);
      List<PyCallableParameter> outerParams = outerType != null ? outerType.getParameters(context) : null;
      if (outerParams == null || outerParams.isEmpty()) return null;

      PyType expectedArgType = outerParams.getFirst().getType(context);
      if (expectedArgType != null && PyTypeChecker.hasGenerics(expectedArgType, context)) {
        expectedArgType = bindGenerics(outerType.getReturnType(context),
                                       expectedResultType(decorators, j, context), expectedArgType, context);
      }
      return expectedArgType;
    }
    return null;
  }

  private static @Nullable PyType bindGenerics(@Nullable PyType declaredResult,
                                               @Nullable PyType expectedResult,
                                               @Nullable PyType typeToResolve,
                                               @NotNull TypeEvalContext context) {
    if (declaredResult == null || expectedResult == null) return typeToResolve;

    PyTypeChecker.GenericSubstitutions substitutions = new PyTypeChecker.GenericSubstitutions();
    if (!PyTypeChecker.match(declaredResult, expectedResult, context, substitutions)) {
      return typeToResolve;
    }
    return PyTypeChecker.substitute(typeToResolve, substitutions, context);
  }

  /**
   * Returns the explicit (non-implicit) parameters of the callable type that {@code decorator}
   * expects its decorated function to have, or {@code null} if the decorator does not declare a
   * usable (typed) parameter signature.
   */
  private static @Nullable List<PyCallableParameter> getExpectedFunctionParameters(@NotNull PyDecorator decorator,
                                                                                   @NotNull TypeEvalContext context) {
    var decoratorCallableType = getDecoratorType(decorator, PyAnyType.getUnknown(), context);
    if (decoratorCallableType == null) return null;

    var decoratorParams = decoratorCallableType.getParameters(context);
    if (decoratorParams == null || decoratorParams.isEmpty()) return null;

    var expectedFuncType = decoratorParams.getFirst().getType(context);
    if (!(expectedFuncType instanceof PyCallableType expectedCallable)) return null;

    var expectedParams = expectedCallable.getParameters(context);
    if (expectedParams == null) return null;

    // Apply implicit offset to skip self-like params in the expected callable
    int implicitOffset = Math.min(expectedCallable.getImplicitOffset(), expectedParams.size());
    return expectedParams.subList(implicitOffset, expectedParams.size());
  }

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
      // A class, or an alias to one, is a typed decorator, not an identity one.
      else if (resolveDecoratorClass(res, context) != null) {
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
        callableReference.getModifier(),
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
        resolvedElements = PySyntheticCallHelper.matchOverloadsByArgumentTypes(overloads, decoratorArgumentTypes, PyAnyType.getUnknown(), context);
      }
    }
    else {
      resolvedElements = List.of(typedElement);
    }

    if (resolvedElements.size() == 1) {
      PyTypedElement resolvedElement = resolvedElements.getFirst();
      PyType type;
      // A decorator may be a class or an alias to one; treat both as a constructor call.
      PyClass decoratorClass = resolveDecoratorClass(resolvedElement, context);
      if (decoratorClass != null) {
        PyCallableType constructorType = buildGenericConstructorType(decoratorClass, context);
        if (constructorType != null) {
          return constructorType;
        }
        type = PyTypeChecker.findGenericDefinitionType(decoratorClass, context);
        if (type instanceof PyClassType classType) {
          type = classType.toClass();
        }
        if (type == null) {
          type = decoratorClass.getType(context);
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

  /** Returns the class a decorator denotes directly or through a {@code Name = SomeClass} alias, else null. */
  private static @Nullable PyClass resolveDecoratorClass(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyClass pyClass) {
      return pyClass;
    }
    if (element instanceof PyTypedElement typedElement &&
        context.getType(typedElement) instanceof PyClassType classType && classType.isDefinition()) {
      return classType.getPyClass();
    }
    return null;
  }

  /**
   * Constructor callable of a generic class decorator: result is the generic definition ({@code C[_T]}) with
   * the (possibly inherited) constructor params remapped to the class's own type parameters. Null if not
   * generic or no usable constructor.
   */
  private static @Nullable PyCallableType buildGenericConstructorType(@NotNull PyClass pyClass, @NotNull TypeEvalContext context) {
    PyCollectionType genericDefinition = PyTypeChecker.findGenericDefinitionType(pyClass, context);
    if (genericDefinition == null) return null;

    List<PyCallableParameter> constructorParameters = new PyClassTypeImpl(pyClass, true).getParameters(context);
    if (constructorParameters == null || constructorParameters.isEmpty()) return null;

    PyTypeChecker.GenericSubstitutions substitutions = PyTypeChecker.INSTANCE.collectTypeSubstitutions(genericDefinition, context);
    List<PyCallableParameter> remappedParameters = ContainerUtil.map(
      constructorParameters,
      parameter -> PyCallableParameterImpl.nonPsi(parameter.getName(),
                                                  PyTypeChecker.substitute(parameter.getType(context), substitutions, context),
                                                  parameter.getDefaultValue()));
    return new PyCallableTypeImpl(remappedParameters, genericDefinition);
  }
}
