package com.jetbrains.python.codeInsight.typing;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.StubAwareComputation;
import com.jetbrains.python.psi.impl.stubs.PyTypingNewTypeStubImpl;
import com.jetbrains.python.psi.stubs.PyTypingNewTypeStub;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.PyTypingNewType;
import com.jetbrains.python.psi.types.PyTypingNewTypeFactoryType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PyTypingNewTypeTypeProvider extends PyTypeProviderBase {

  @Override
  public @Nullable Ref<PyType> getCallType(@NotNull PyFunction function,
                                           @NotNull PyCallSiteExpression callSite,
                                           @NotNull TypeEvalContext context) {
    PyClass aClass = PyUtil.turnConstructorIntoClass(function);
    PyQualifiedNameOwner qualifiedNameOwner = aClass != null ? aClass : function;
    if (callSite instanceof PyCallExpression callExpression &&
        PyTypingTypeProvider.NEW_TYPE.equals(qualifiedNameOwner.getQualifiedName())) {
      if (context.maySwitchToAST(callSite)) {
        final PyTypingNewTypeStub stub = PyTypingNewTypeStubImpl.Companion.create(callExpression);
        if (stub != null) {
          final PyClassType type = getClassType(stub, context, callSite);
          if (type != null) {
            PyTypingNewType newType = new PyTypingNewType(type, stub.getName(), getDeclaration(callExpression));
            return Ref.create(new PyTypingNewTypeFactoryType(newType));
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
    final PyClassType result = PyUtil.as(type, PyClassType.class);
    if (result != null) {
      return PyUtil.as(result.toClass(), PyClassType.class);
    }
    return null;
  }

  @Override
  public @Nullable Ref<@Nullable PyCallableType> prepareCalleeTypeForCall(@Nullable PyType type,
                                                                          @NotNull PyCallExpression call,
                                                                          @NotNull TypeEvalContext context) {
    return type instanceof PyTypingNewType ? Ref.create((PyTypingNewType)type) : null;
  }

  private static @Nullable PyTargetExpression getDeclaration(@NotNull PyCallExpression call) {
    final PsiElement parent = call.getParent();
    if (parent instanceof PyAssignmentStatement) {
      return PyUtil.as(((PyAssignmentStatement)parent).getLeftHandSideExpression(), PyTargetExpression.class);
    }
    return null;
  }
}
