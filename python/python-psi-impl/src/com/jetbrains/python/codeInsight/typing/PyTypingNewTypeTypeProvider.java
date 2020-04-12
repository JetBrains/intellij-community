package com.jetbrains.python.codeInsight.typing;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.stubs.PyTypingNewTypeStub;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.*;

public class PyTypingNewTypeTypeProvider extends PyTypeProviderBase {

  @Override
  public @Nullable Ref<PyType> getCallType(@NotNull PyFunction function,
                                           @NotNull PyCallSiteExpression callSite,
                                           @NotNull TypeEvalContext context) {
    if (callSite instanceof PyCallExpression && NEW_TYPE.equals(function.getQualifiedName())) {
      return Ref.create(getNewTypeForCallExpression((PyCallExpression)callSite, context));
    }

    return null;
  }

  @Override
  public @Nullable PyType getReferenceExpressionType(@NotNull PyReferenceExpression referenceExpression, @NotNull TypeEvalContext context) {
    final PyType newType = getNewTypeForReference(referenceExpression, context);
    if (newType != null) {
      return newType;
    }
    return null;
  }

  @Override
  public Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyTargetExpression) {
      final PyType newType = getNewTypeCreationForTarget((PyTargetExpression)referenceTarget, context);
      if (newType != null) {
        return Ref.create(newType);
      }
    }

    return null;
  }

  @Nullable
  private static PyType getNewTypeForReference(@NotNull PyReferenceExpression referenceExpression, @NotNull TypeEvalContext context) {
    final PyCallExpression callee = PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression);
    if (callee == null) {
      return null;
    }
    final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
    final ResolveResult[] resolveResults = referenceExpression.getReference(resolveContext).multiResolve(false);

    for (PsiElement element : PyUtil.filterTopPriorityResults(resolveResults)) {
      if (element instanceof PyTargetExpression) {
        final PyType typeForTarget = getNewTypeCreationForTarget((PyTargetExpression)element, context);
        if (typeForTarget != null) {
          return typeForTarget;
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyType getNewTypeCreationForTarget(@NotNull PyTargetExpression referenceTarget, @NotNull TypeEvalContext context) {
    final PyTargetExpressionStub stub = referenceTarget.getStub();
    if (stub != null) {
      final PyTypingNewTypeStub customStub = stub.getCustomStub(PyTypingNewTypeStub.class);
      if (customStub != null) {
        final PyType type = Ref.deref(getStringBasedType(customStub.getClassType(), referenceTarget, context));
        if (type instanceof PyClassType) {
          return new PyTypingNewType((PyClassType)type, true, customStub.getName());
        }
      }
    }
    else {
      final PyExpression value = referenceTarget.findAssignedValue();
      if (value instanceof PyCallExpression) {
        return getNewTypeForCallExpression(((PyCallExpression)value), context);
      }
    }
    return null;
  }

  @Nullable
  private static PyType getNewTypeForCallExpression(@NotNull PyCallExpression callExpression, @NotNull TypeEvalContext context) {
    if (PyTypingNewType.Companion.isTypingNewType(callExpression)) {
      final String className = PyResolveUtil.resolveStrArgument(callExpression, 0, "name");
      if (className != null) {
        PyExpression secondArg = PyPsiUtils.flattenParens(callExpression.getArgument(1, PyExpression.class));
        if (secondArg != null) {
          final Ref<PyType> argType = getType(secondArg, context);
          if (argType != null) {
            final PyType type = argType.get();
            if (type instanceof PyClassType) {
              return new PyTypingNewType((PyClassType)type, true, className);
            }
          }
        }
      }
    }
    return null;
  }
}
