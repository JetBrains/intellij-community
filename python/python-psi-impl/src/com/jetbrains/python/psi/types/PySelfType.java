package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PySelfType implements PyTypeParameterType, PyClassType {
  private final @NotNull PyClassType myScopeClassType;

  public PySelfType(@NotNull PyClassType scopeClassType) {
    myScopeClassType = scopeClassType;
  }

  @Override
  public @Nullable List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                                    @Nullable PyExpression location,
                                                                    @NotNull AccessDirection direction,
                                                                    @NotNull PyResolveContext resolveContext) {
    return myScopeClassType.resolveMember(name, location, direction, resolveContext);
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix,
                                        PsiElement location,
                                        ProcessingContext context) {
    return myScopeClassType.getCompletionVariants(completionPrefix, location, context);
  }

  @Override
  public @NotNull String getName() {
    return "Self";
  }

  @Override
  public @NotNull PyQualifiedNameOwner getScopeOwner() {
    return myScopeClassType.getPyClass();
  }

  public @NotNull PyClassType getScopeClassType() {
    return myScopeClassType;
  }

  /**
   * @return true if type[Self], false otherwise
   */
  @Override
  public boolean isDefinition() {
    return myScopeClassType.isDefinition();
  }

  @Override
  public @NotNull PySelfType toInstance() {
    return new PySelfType(myScopeClassType.toInstance());
  }

  @Override
  public @NotNull PySelfType toClass() {
    return new PySelfType(myScopeClassType.toClass());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PySelfType type = (PySelfType)o;

    if (!Objects.equals(myScopeClassType, type.myScopeClassType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myScopeClassType.hashCode();
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {

  }

  @Override
  public @Nullable String getClassQName() {
    return myScopeClassType.getClassQName();
  }

  @Override
  public @NotNull List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context) {
    return myScopeClassType.getSuperClassTypes(context);
  }

  @Override
  public @Nullable List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                                    @Nullable PyExpression location,
                                                                    @NotNull AccessDirection direction,
                                                                    @NotNull PyResolveContext resolveContext,
                                                                    boolean inherited) {
    return myScopeClassType.resolveMember(name, location, direction, resolveContext, inherited);
  }

  @Override
  public void visitMembers(@NotNull Processor<? super PsiElement> processor, boolean inherited, @NotNull TypeEvalContext context) {
    myScopeClassType.visitMembers(processor, inherited, context);
  }

  @Override
  public @NotNull Set<String> getMemberNames(boolean inherited, @NotNull TypeEvalContext context) {
    return myScopeClassType.getMemberNames(inherited, context);
  }

  @Override
  public @NotNull List<@NotNull PyTypeMember> getAllMembers(@NotNull PyResolveContext resolveContext) {
    return myScopeClassType.getAllMembers(resolveContext);
  }

  @Override
  public @NotNull List<@NotNull PyTypeMember> findMember(@NotNull String name, @NotNull PyResolveContext resolveContext) {
    return myScopeClassType.findMember(name, resolveContext);
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public @Nullable PyClassLikeType getMetaClassType(@NotNull TypeEvalContext context, boolean inherited) {
    return myScopeClassType.getMetaClassType(context, inherited);
  }

  @Override
  public boolean isCallable() {
    return myScopeClassType.isCallable();
  }

  @Override
  public @Nullable PyType getReturnType(@NotNull TypeEvalContext context) {
    if (isDefinition()) {
      return toInstance();
    }
    else {
      return myScopeClassType.getReturnType(context);
    }
  }

  @Override
  public @Nullable PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    if (isDefinition()) {
      return toInstance();
    }
    else {
      return myScopeClassType.getCallType(context, callSite);
    }
  }

  @Override
  public @Nullable List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return myScopeClassType.getParameters(context);
  }

  @Override
  public @Nullable PyCallable getCallable() {
    return myScopeClassType.getCallable();
  }

  @Override
  public @Nullable PyFunction.Modifier getModifier() {
    return myScopeClassType.getModifier();
  }

  @Override
  public int getImplicitOffset() {
    return myScopeClassType.getImplicitOffset();
  }

  @Override
  public @NotNull PyClass getPyClass() {
    return myScopeClassType.getPyClass();
  }

  @Override
  public <T> @Nullable T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {

  }

  @Override
  public boolean isAttributeWritable(@NotNull String name, @NotNull TypeEvalContext context) {
    return myScopeClassType.isAttributeWritable(name, context);
  }

  @Override
  public <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
      return visitorExt.visitPySelfType(this);
    }
    return visitor.visitPyType(this);
  }

  @Override
  public @NotNull List<PyClassLikeType> getAncestorTypes(@NotNull TypeEvalContext context) {
    return myScopeClassType.getAncestorTypes(context);
  }
}
