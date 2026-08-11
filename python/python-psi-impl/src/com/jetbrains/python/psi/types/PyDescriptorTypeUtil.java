package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
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

  public static @Nullable Ref<PyType> getDunderGetReturnType(@NotNull PyQualifiedExpression expression,
                                                             @Nullable PyType instanceType,
                                                             @Nullable PyType attributeType,
                                                             @NotNull TypeEvalContext context) {
    if (!expression.isQualified()) return null;
    if (attributeType instanceof PyUnionType unionType) {
      return mapDescriptorUnion(unionType, member -> getDunderGetReturnType(expression, instanceType, member, context));
    }

    final PyClassLikeType targetType = as(attributeType, PyClassLikeType.class);
    if (targetType == null || targetType.isDefinition()) return null;

    final PyResolveContext resolveContext = PyResolveContext.noProperties(context);
    final List<? extends RatedResolveResult> members = targetType.resolveMember(PyNames.DUNDER_GET, expression, AccessDirection.READ,
                                                                                resolveContext);
    if (members == null || members.isEmpty()) return null;

    return getTypeFromSyntheticDunderGetCall(expression, instanceType, attributeType, context);
  }

  public static @Nullable Ref<PyType> getExpectedValueTypeForDunderSet(@NotNull PyQualifiedExpression targetExpression,
                                                                       @Nullable PyType attributeType,
                                                                       @NotNull TypeEvalContext context) {
    if (attributeType instanceof PyUnionType unionType) {
      return mapDescriptorUnion(unionType, member -> getExpectedValueTypeForDunderSet(targetExpression, member, context));
    }

    final PyClassLikeType targetType = as(attributeType, PyClassLikeType.class);
    if (targetType == null || targetType.isDefinition()) return null;

    final PyResolveContext resolveContext = PyResolveContext.noProperties(context);
    final List<? extends RatedResolveResult> members = targetType.resolveMember(PyNames.DUNDER_SET, targetExpression, AccessDirection.READ,
                                                                                resolveContext);
    if (members == null || members.isEmpty()) return null;

    return getExpectedTypeFromDunderSet(targetExpression, attributeType, context);
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

  private static @Nullable Ref<PyType> getTypeFromSyntheticDunderGetCall(@NotNull PyQualifiedExpression expression,
                                                                         @Nullable PyType instanceType,
                                                                         @NotNull PyType attributeType,
                                                                         @NotNull TypeEvalContext context) {
    if (attributeType instanceof PyCallableType receiverType && instanceType instanceof PyClassLikeType classType) {
      PyType instanceArgumentType;
      PyType instanceTypeArgument;
      final var noneType = PyBuiltinCache.getInstance(expression).getNoneType();
      if (noneType == null) {
        return null;
      }
      if (classType.isDefinition()) {
        instanceArgumentType = noneType;
        instanceTypeArgument = classType;
      }
      else {
        instanceArgumentType = classType;
        instanceTypeArgument = classType.toClass();
      }
      List<PyType> argumentTypes = List.of(instanceArgumentType, instanceTypeArgument);
      PyType type = PySyntheticCallHelper.getCallTypeByFunctionName(PyNames.DUNDER_GET, receiverType, argumentTypes, context);
      return Ref.create(type);
    }
    return null;
  }

  private static @Nullable Ref<PyType> getExpectedTypeFromDunderSet(@NotNull PyQualifiedExpression expression,
                                                                    @NotNull PyType attributeType,
                                                                    @NotNull TypeEvalContext context) {
    PyExpression qualifier = expression.getQualifier();
    PyType objectArgumentType = PyBuiltinCache.getInstance(expression).getNoneType();
    PyType valueArgumentType = PyAnyType.getUnknown(); // We don't use the actual type of value here as we want to match the overload by object type only

    if (qualifier != null && attributeType instanceof PyCallableType) {
      PyType qualifierType = context.getType(qualifier);
      if (qualifierType instanceof PyClassType classType && !classType.isDefinition()) {
        objectArgumentType = qualifierType; // TODO: Incorrect: can be union
      }
    }
    List<PyType> argumentTypes = new ArrayList<>();
    argumentTypes.add(objectArgumentType);
    argumentTypes.add(valueArgumentType);

    List<PyFunction> functions =
      PySyntheticCallHelper.resolveFunctionsByArgumentTypes(PyNames.DUNDER_SET, argumentTypes, attributeType, context);

    if (functions.isEmpty()) return null;

    return Ref.create(getExpectedDunderSetValueType(functions.get(0), attributeType, context));
  }

  private static @Nullable PyType getExpectedDunderSetValueType(@NotNull PyFunction function,
                                                                @NotNull PyType receiverType,
                                                                @NotNull TypeEvalContext context) {
    List<PyCallableParameter> parameters = function.getParameters(context);
    if (parameters.size() != 3) return null;
    // Parameter names may differ, but 'value' parameter should always be the third one
    PyCallableParameter valueParameter = parameters.get(2);
    if (valueParameter != null) {
      PyType type = valueParameter.getArgumentType(context);
      if (type != null && receiverType instanceof PyClassType) {
        PyTypeChecker.GenericSubstitutions subs = PyTypeChecker.unifyReceiver(receiverType, context);
        return PyTypeChecker.substitute(type, subs, context);
      }
    }
    return null;
  }
}
