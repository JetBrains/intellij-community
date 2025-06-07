// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A pack of constraints that limit behavior of {@link TypeEvalContext}.
 * Any two  {@link TypeEvalContext}s may share their cache if their constraints are equal and no PSI changes
 * happened between their creation.
 * <p/>
 * This class created to support hash/equals for context.
 *
 * @author Ilya.Kazakevich
 */
@ApiStatus.Internal
public final class TypeEvalConstraints {
  final boolean myAllowDataFlow;
  final boolean myAllowStubToAST;
  final boolean myAllowCallContext;
  final @Nullable PsiFile myOrigin;

  /**
   * @see TypeEvalContext
   */
  TypeEvalConstraints(final boolean allowDataFlow, final boolean allowStubToAST, final boolean allowCallContext,
                      final @Nullable PsiFile origin) {
    myAllowDataFlow = allowDataFlow;
    myAllowStubToAST = allowStubToAST;
    myAllowCallContext = allowCallContext;
    myOrigin = origin;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeEvalConstraints that)) return false;

    return myAllowDataFlow == that.myAllowDataFlow && 
           myAllowStubToAST == that.myAllowStubToAST &&
           myAllowCallContext == that.myAllowCallContext &&
           Objects.equals(myOrigin, that.myOrigin);
  }

  @Override
  public int hashCode() {
    int result = (myAllowDataFlow ? 1 : 0);
    result = 31 * result + (myAllowStubToAST ? 1 : 0);
    result = 31 * result + (myOrigin != null ? myOrigin.hashCode() : 0);
    result = 31 * result + (myAllowCallContext ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return String.format("TypeEvalConstraints(%b, %b, %b, %s)", myAllowDataFlow, myAllowStubToAST, myAllowCallContext, myOrigin);
  }
}
