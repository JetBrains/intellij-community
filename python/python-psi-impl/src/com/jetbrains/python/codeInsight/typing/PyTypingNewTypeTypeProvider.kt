package com.jetbrains.python.codeInsight.typing;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.StubAwareComputation;
import com.jetbrains.python.psi.impl.stubs.PyTypingNewTypeStubImpl;
import com.jetbrains.python.psi.stubs.PyTypingNewTypeStub;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyCallableTypeImpl;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.PyTypingNewType;
import com.jetbrains.python.psi.types.PyTypingNewTypeFactoryType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class PyTypingNewTypeTypeProvider extends PyTypeProviderBase {

  @Override
  public @Nullable Ref<PyType> getCallType(@NotNull PyFunction function,
                                           @NotNull PyCallSiteExpression callSite,
                                           @NotNull TypeEvalContext context) {
    if (callSite instanceof PyCallExpression callExpression &&
        PyTypingTypeProvider.NEW_TYPE.equals(function.getQualifiedName())) {
      PyTypingNewTypeFactoryType newType = createNewType(callExpression, context);
      if (newType != null) {
        return Ref.create(newType);
      }
    }
    return null;
  }

  private static @Nullable PyTypingNewTypeFactoryType createNewType(@NotNull PyCallExpression callExpression,
                                                                    @NotNull TypeEvalContext context) {
    if (context.maySwitchToAST(callExpression)) {
      if (callExpression.getParent() instanceof PyAssignmentStatement assignmentStatement) {
        if (assignmentStatement.getLeftHandSideExpression() instanceof PyTargetExpression targetExpression) {
          final PyTypingNewTypeStub stub = PyTypingNewTypeStubImpl.Companion.create(callExpression);
          if (stub != null) {
            final PyClassType type = getClassType(stub, context, callExpression);
            if (type != null) {
              PyTypingNewType newType = new PyTypingNewType(type, stub.getName(), targetExpression);
              return new PyTypingNewTypeFactoryType(newType);
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    PyTypingNewType newType = getNewTypeForResolvedElement(referenceTarget, context);
    if (newType != null) {
      return Ref.create(new PyTypingNewTypeFactoryType(newType));
    }
    return null;
  }

  static @Nullable PyTypingNewType getNewTypeForResolvedElement(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyTargetExpression targetExpression) {
      return StubAwareComputation.on(targetExpression)
        .withCustomStub(stub -> stub.getCustomStub(PyTypingNewTypeStub.class))
        .overStub(customStub -> getNewTypeFromStub(targetExpression, customStub, context))
        .withStubBuilder(PyTypingNewTypeStubImpl.Companion::create)
        .compute(context);
    }
    return null;
  }

  private static @Nullable PyTypingNewType getNewTypeFromStub(@NotNull PyTargetExpression target,
                                                              @Nullable PyTypingNewTypeStub stub,
                                                              @NotNull TypeEvalContext context) {
    if (stub == null) return null;
    final PyClassType type = getClassType(stub, context, target);
    return type != null ? new PyTypingNewType(type, stub.getName(), target) : null;
  }

  private static @Nullable PyClassType getClassType(@NotNull PyTypingNewTypeStub stub,
                                                    @NotNull TypeEvalContext context,
                                                    @NotNull PsiElement anchor) {
    final PyType type = Ref.deref(PyTypingTypeProvider.getStringBasedType(stub.getClassType(), anchor, context));
    if (type instanceof PyClassType classType) {
      return classType.toClass();
    }
    return null;
  }

  @Override
  public @Nullable Ref<@NotNull PyCallableType> prepareCalleeTypeForCall(@Nullable PyType type,
                                                                         @NotNull PyCallExpression call,
                                                                         @NotNull TypeEvalContext context) {
    if (type instanceof PyClassType classType && PyTypingTypeProvider.NEW_TYPE.equals(classType.getClassQName())) {
      PyTypingNewTypeFactoryType newType = createNewType(call, context);
      if (newType != null) {
        List<PyCallableParameter> parameters = classType.toClass().getParameters(context);
        return Ref.create(new PyCallableTypeImpl(parameters, newType));
      }
    }
    return null;
  }
}
