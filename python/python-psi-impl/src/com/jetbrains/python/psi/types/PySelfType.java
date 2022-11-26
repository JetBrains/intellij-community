package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class PySelfType implements PyType {
  @NotNull private final PyClassLikeType myScopeClassType;

  public PySelfType(@NotNull PyClassLikeType scopeClassType) {
    myScopeClassType = scopeClassType.toInstance();
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
  @NotNull
  public String getName() {
    return "Self";
  }

  @NotNull
  public PyClassLikeType getScopeClassType() {
    return myScopeClassType;
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
}
