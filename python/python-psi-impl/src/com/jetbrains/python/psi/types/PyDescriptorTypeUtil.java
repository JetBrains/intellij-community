package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.jetbrains.python.psi.PyUtil.as;

@ApiStatus.Internal
public final class PyDescriptorTypeUtil {

  private PyDescriptorTypeUtil() { }

  public static @Nullable PyType applyDescriptorGet(@NotNull PyInstantiableType<?> instanceType,
                                                    @Nullable PyType attributeType,
                                                    @NotNull PyClassType noneType,
                                                    @NotNull TypeEvalContext context) {
    List<PyCallableArgument> arguments = instanceType.isDefinition()
                                         ? List.of(new PyCallableArgument(noneType), new PyCallableArgument(instanceType))
                                         : List.of(new PyCallableArgument(instanceType), new PyCallableArgument(instanceType.toClass()));
    return PyTypeUtil.compositeMap(attributeType, type -> {
      Ref<PyType> dunderGetCallType = PyCallExpressionHelper.getSpecialMethodCallType(type, PyNames.DUNDER_GET, arguments, context);
      return dunderGetCallType != null ? dunderGetCallType.get() : type;
    });
  }

  public static @Nullable Ref<PyType> getExpectedValueTypeForDunderSet(@NotNull PyQualifiedExpression expression,
                                                                       @Nullable PyType attributeType,
                                                                       @NotNull TypeEvalContext context) {
    if (attributeType instanceof PyUnionType unionType) {
      return mapDescriptorUnion(unionType, member -> getExpectedValueTypeForDunderSet(expression, member, context));
    }

    final PyClassLikeType targetType = as(attributeType, PyClassLikeType.class);
    if (targetType == null || targetType.isDefinition()) return null;

    final List<? extends RatedResolveResult> resolvedDunderSet = targetType.resolveMember(PyNames.DUNDER_SET, expression,
                                                                                          AccessDirection.READ,
                                                                                          PyResolveContext.noProperties(context));
    if (ContainerUtil.isEmpty(resolvedDunderSet)) return null;

    PyType dunderSetType = PyTypeUtil.getTypeOfBoundMember(targetType, resolvedDunderSet, context);
    return getExpectedTypeFromDunderSet(expression, dunderSetType, context);
  }

  /**
   * Resolves each union member via {@code resolver}: a non-null result replaces the member, others are kept.
   * Returns {@code null} when no member resolved, so plain unions keep their original type.
   */
  private static @Nullable Ref<PyType> mapDescriptorUnion(@NotNull PyUnionType unionType,
                                                          @NotNull Function<@Nullable PyType, @Nullable Ref<PyType>> resolver) {
    boolean anyDescriptor = false;
    final List<PyType> mapped = new ArrayList<>();
    for (PyType member : unionType.getMembers()) {
      final Ref<PyType> resolved = resolver.apply(member);
      if (resolved != null) {
        anyDescriptor = true;
        mapped.add(resolved.get());
      }
      else {
        mapped.add(member);
      }
    }
    if (!anyDescriptor) return null;
    return Ref.create(PyUnionType.union(mapped));
  }

  private static @Nullable Ref<PyType> getExpectedTypeFromDunderSet(@NotNull PyQualifiedExpression expression,
                                                                    @Nullable PyType setMethodType,
                                                                    @NotNull TypeEvalContext context) {
    PyExpression qualifier = expression.getQualifier();
    PyType objectArgumentType = PyBuiltinCache.getInstance(expression).getNoneType();
    PyType valueArgumentType = PyAnyType.getUnknown(); // We don't use the actual type of value here as we want to match the overload by object type only

    if (qualifier != null) {
      PyType qualifierType = context.getType(qualifier);
      if (qualifierType instanceof PyClassType classType && !classType.isDefinition()) {
        objectArgumentType = qualifierType; // TODO: Incorrect: can be union
      }
    }
    List<PyCallableArgument> arguments = List.of(
      new PyCallableArgument(objectArgumentType),
      new PyCallableArgument(valueArgumentType)
    );

    List<@NotNull PyCallableType> setMethodOverloads = PyTypeUtil.getCallableItems(setMethodType);
    if (setMethodType instanceof PyOverloadType) {
      setMethodOverloads = PyCallExpressionHelper.selectMatchingOverloads(setMethodOverloads, arguments, context);
    }
    PyCallableType setMethod = ContainerUtil.getFirstItem(setMethodOverloads);
    if (setMethod == null) return null;

    // Parameter names may differ, but 'value' parameter should always be the second one of a bound `__set__`
    List<PyCallableParameter> parameters = setMethod.getParameters(context);
    PyCallableParameter valueParameter = parameters != null && parameters.size() == 2 ? parameters.get(1) : null;
    return Ref.create(valueParameter != null ? valueParameter.getArgumentType(context) : null);
  }
}
