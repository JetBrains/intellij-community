package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

@ApiStatus.Internal
public final class PyDescriptorTypeUtil {

  private PyDescriptorTypeUtil() { }

  public static @Nullable Ref<PyType> getDunderGetReturnType(@NotNull PyQualifiedExpression expression,
                                                             @Nullable PyType attributeType,
                                                             @NotNull TypeEvalContext context) {
    if (!expression.isQualified()) return null;
    final PyClassLikeType targetType = as(attributeType, PyClassLikeType.class);
    if (targetType == null || targetType.isDefinition()) return null;

    final PyResolveContext resolveContext = PyResolveContext.noProperties(context);
    final List<? extends RatedResolveResult> members = targetType.resolveMember(PyNames.DUNDER_GET, expression, AccessDirection.READ,
                                                                                resolveContext);
    if (members == null || members.isEmpty()) return null;

    return getTypeFromSyntheticDunderGetCall(expression, attributeType, context);
  }

  public static @Nullable Ref<PyType> getExpectedValueTypeForDunderSet(@NotNull PyTargetExpression targetExpression,
                                                                       @Nullable PyType attributeType,
                                                                       @NotNull TypeEvalContext context) {
    final PyClassLikeType targetType = as(attributeType, PyClassLikeType.class);
    if (targetType == null || targetType.isDefinition()) return null;

    final PyResolveContext resolveContext = PyResolveContext.noProperties(context);
    final List<? extends RatedResolveResult> members = targetType.resolveMember(PyNames.DUNDER_SET, targetExpression, AccessDirection.READ,
                                                                                resolveContext);
    if (members == null || members.isEmpty()) return null;

    return getExpectedTypeFromDunderSet(targetExpression, attributeType, context);
  }

  private static @Nullable Ref<PyType> getTypeFromSyntheticDunderGetCall(@NotNull PyQualifiedExpression expression,
                                                                         @NotNull PyType attributeType,
                                                                         @NotNull TypeEvalContext context) {
    PyExpression qualifier = expression.getQualifier();
    if (qualifier != null && attributeType instanceof PyCallableType receiverType) {
      PyType qualifierType = context.getType(qualifier);
      if (qualifierType instanceof PyClassType classType) {
        PyType instanceArgumentType;
        PyType instanceTypeArgument;
        final var noneType = PyBuiltinCache.getInstance(expression).getNoneType();
        if (classType.isDefinition()) {
          instanceArgumentType = noneType;
          instanceTypeArgument = classType;
        }
        else {
          instanceArgumentType = classType;
          instanceTypeArgument = noneType;
        }
        List<PyType> argumentTypes = List.of(instanceArgumentType, instanceTypeArgument);
        PyType type  = PySyntheticCallHelper.getCallTypeByFunctionName(PyNames.DUNDER_GET, receiverType, argumentTypes, context);
        return Ref.create(type);
      }
    }
    return null;
  }

  private static @Nullable Ref<PyType> getExpectedTypeFromDunderSet(@NotNull PyQualifiedExpression expression,
                                                                    @NotNull PyType attributeType,
                                                                    @NotNull TypeEvalContext context) {
    PyExpression qualifier = expression.getQualifier();
    PyType objectArgumentType = PyBuiltinCache.getInstance(expression).getNoneType();
    PyType valueArgumentType = null; // We don't use the actual type of value here as we want to match the overload by object type only

    if (qualifier != null && attributeType instanceof PyCallableType) {
      PyType qualifierType = context.getType(qualifier);
      if (qualifierType instanceof PyClassType classType && !classType.isDefinition()) {
        objectArgumentType = qualifierType;
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
