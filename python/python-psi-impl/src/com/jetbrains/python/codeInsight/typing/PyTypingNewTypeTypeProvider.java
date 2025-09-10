package com.jetbrains.python.codeInsight.typing;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.StubAwareComputation;
import com.jetbrains.python.psi.impl.stubs.PyTypingNewTypeStubImpl;
import com.jetbrains.python.psi.stubs.PyTypingNewTypeStub;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyTypingNewTypeTypeProvider extends PyTypeProviderBase {

  @Override
  public @Nullable Ref<PyType> getCallType(@NotNull PyFunction function,
                                           @NotNull PyCallSiteExpression callSite,
                                           @NotNull TypeEvalContext context) {
    PyClass aClass = PyUtil.turnConstructorIntoClass(function);
    PyQualifiedNameOwner qualifiedNameOwner = aClass != null ? aClass : function;
    return callSite instanceof PyCallExpression && PyTypingTypeProvider.NEW_TYPE.equals(qualifiedNameOwner.getQualifiedName())
           ? PyTypeUtil.notNullToRef(getNewTypeFromAST((PyCallExpression)callSite, context))
           : null;
  }

  @Override
  public Ref<PyType> getReferenceType(@NotNull PsiElement referenceTarget, @NotNull TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyTargetExpression) {
      return PyTypeUtil.notNullToRef(getNewTypeForTarget((PyTargetExpression)referenceTarget, context));
    }

    return null;
  }

  private static @Nullable PyTypingNewType getNewTypeForTarget(@NotNull PyTargetExpression target, @NotNull TypeEvalContext context) {
    return StubAwareComputation.on(target)
      .withCustomStub(stub -> stub.getCustomStub(PyTypingNewTypeStub.class))
      .overStub(customStub -> getNewTypeFromStub(target, customStub, context))
      .withStubBuilder(PyTypingNewTypeStubImpl.Companion::create)
      .compute(context);
  }

  private static @Nullable PyTypingNewType getNewTypeFromStub(@NotNull PyTargetExpression target,
                                                              @Nullable PyTypingNewTypeStub stub,
                                                              @NotNull TypeEvalContext context) {
    if (stub == null) return null;
    final PyClassType type = getClassType(stub, context, target);
    return type != null ? new PyTypingNewType(type, stub.getName(), target) : null;
  }

  private static @Nullable PyTypingNewType getNewTypeFromStub(@NotNull PyCallExpression call,
                                                              @Nullable PyTypingNewTypeStub stub,
                                                              @NotNull TypeEvalContext context) {
    if (stub == null) return null;
    final PyClassType type = getClassType(stub, context, call);
    return type != null ? new PyTypingNewType(type, stub.getName(), getDeclaration(call)) : null;
  }

  private static @Nullable PyTypingNewType getNewTypeFromAST(@NotNull PyCallExpression call, @NotNull TypeEvalContext context) {
    if (!context.maySwitchToAST(call)) return null;
    return getNewTypeFromStub(call, PyTypingNewTypeStubImpl.Companion.create(call), context);
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
