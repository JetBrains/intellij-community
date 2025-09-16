package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PySelfType implements PyTypeParameterType, PyClassLikeType {
  private final @NotNull PyClassType myScopeClassType;
  private @Nullable PyClass matchingScope = null;

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

  public static @Nullable PyType extractScopeClassTypeIfNeeded(@Nullable PyType type) {
    if (type instanceof PySelfType selfType) {
      return selfType.myScopeClassType;
    }
    return type;
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

  /**
   * The presense of this field indicates that we perform type-check inside the class that this type belongs to.
   * It means that some special rules should be applied, e.g.:
   * <pre>
   * {@code
   *   class Example:
   *      def returns_instance(self) -> Self:
   *          return Example() # Error
   *
   *       def returns_self(self) -> Self:
   *          return self # OK
   * }
   * </pre>
   */
  @Nullable
  public PyClass getMatchingScope() {
    return matchingScope;
  }

  public void setMatchingScope(@Nullable PyClass matchingScopeClass) {
    this.matchingScope = matchingScopeClass;
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
