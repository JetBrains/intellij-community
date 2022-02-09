// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


public final class PyResolveContext {
  private final boolean myAllowImplicits;
  private final boolean myAllowProperties;
  private final boolean myAllowRemote;

  @NotNull
  private final TypeEvalContext myTypeEvalContext;

  private PyResolveContext(boolean allowImplicits, boolean allowProperties, boolean allowRemote, @NotNull TypeEvalContext typeEvalContext) {
    myAllowImplicits = allowImplicits;
    myAllowProperties = allowProperties;
    myAllowRemote = allowRemote;
    myTypeEvalContext = typeEvalContext;
  }

  public boolean allowImplicits() {
    return myAllowImplicits;
  }

  public boolean allowProperties() {
    return myAllowProperties;
  }

  public boolean allowRemote() {
    return myAllowRemote;
  }

  /**
   * @deprecated Please use {@link PyResolveContext#defaultContext(TypeEvalContext)}
   * to explicitly specify type evaluation context.
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public static PyResolveContext defaultContext() {
    return new PyResolveContext(false, true, false, TypeEvalContext.codeInsightFallback(null));
  }

  @NotNull
  public static PyResolveContext defaultContext(@NotNull TypeEvalContext context) {
    return new PyResolveContext(false, true, false, context);
  }

  /**
   * @deprecated Please use {@link PyResolveContext#implicitContext(TypeEvalContext)}
   * to explicitly specify type evaluation context.
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public static PyResolveContext implicitContext() {
    return new PyResolveContext(true, true, false, TypeEvalContext.codeInsightFallback(null));
  }

  /**
   * Allow searching for dynamic usages based on duck typing and guesses during resolve.
   *
   * Note that this resolve context is slower than the default one. Use it only for one-off user actions.
   */
  @NotNull
  public static PyResolveContext implicitContext(@NotNull TypeEvalContext context) {
    return new PyResolveContext(true, true, false, context);
  }

  @NotNull
  public static PyResolveContext noProperties(@NotNull TypeEvalContext context) {
    return new PyResolveContext(false, false, false, context);
  }

  @NotNull
  public PyResolveContext withTypeEvalContext(@NotNull TypeEvalContext context) {
    return new PyResolveContext(myAllowImplicits, myAllowProperties, myAllowRemote, context);
  }

  @NotNull
  public PyResolveContext withoutImplicits() {
    return allowImplicits() ? new PyResolveContext(false, myAllowProperties, myAllowRemote, myTypeEvalContext) : this;
  }

  @NotNull
  public PyResolveContext withRemote() {
    return allowRemote() ? this : new PyResolveContext(myAllowImplicits, myAllowProperties, true, myTypeEvalContext);
  }

  @NotNull
  public TypeEvalContext getTypeEvalContext() {
    return myTypeEvalContext;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyResolveContext that = (PyResolveContext)o;

    if (myAllowImplicits != that.myAllowImplicits) return false;
    if (myAllowProperties != that.myAllowProperties) return false;
    if (myAllowRemote != that.myAllowRemote) return false;
    if (!myTypeEvalContext.equals(that.myTypeEvalContext)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myAllowImplicits ? 1 : 0);
    result = 31 * result + (myAllowProperties ? 1 : 0);
    result = 31 * result + (myAllowRemote ? 1 : 0);
    result = 31 * result + myTypeEvalContext.hashCode();
    return result;
  }
}
