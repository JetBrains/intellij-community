package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

public final class PyDescriptorTypeUtil {

  private PyDescriptorTypeUtil() { }

  @Nullable
  public static Ref<PyType> getDescriptorType(@NotNull PyQualifiedExpression expression, @Nullable PyType typeFromTargets, @NotNull TypeEvalContext context) {
    if (!expression.isQualified()) return null;
    final PyClassLikeType targetType = as(typeFromTargets, PyClassLikeType.class);
    if (targetType == null || targetType.isDefinition()) return null;

    final PyResolveContext resolveContext = PyResolveContext.noProperties(context);
    final List<? extends RatedResolveResult> members = targetType.resolveMember(PyNames.GET, expression, AccessDirection.READ,
                                                                                resolveContext);
    if (members == null || members.isEmpty()) return null;

    return getTypeFromSyntheticDunderGetCall(expression, typeFromTargets, context);
  }

  @Nullable
  private static Ref<PyType> getTypeFromSyntheticDunderGetCall(@NotNull PyQualifiedExpression expression, @NotNull PyType typeFromTargets, @NotNull TypeEvalContext context) {
    PyExpression qualifier = expression.getQualifier();
    if (qualifier != null && typeFromTargets instanceof PyCallableType receiverType) {
      PyType qualifierType = context.getType(qualifier);
      if (qualifierType instanceof PyClassType classType) {
        PyType instanceArgumentType;
        PyType instanceTypeArgument;
        if (classType.isDefinition()) {
          instanceArgumentType = PyNoneType.INSTANCE;
          instanceTypeArgument = classType;
        }
        else {
          instanceArgumentType = classType;
          instanceTypeArgument = PyNoneType.INSTANCE;
        }
        List<PyType> argumentTypes = List.of(instanceArgumentType, instanceTypeArgument);
        PyType type  = PySyntheticCallHelper.getCallTypeByFunctionName(PyNames.GET, receiverType, argumentTypes, context);
        return Ref.create(type);
      }
    }
    return null;
  }

}
