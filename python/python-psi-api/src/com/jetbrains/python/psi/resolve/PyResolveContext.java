// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve;

import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;


public final class PyResolveContext {
  private final boolean myAllowImplicits;
  private final boolean myAllowProperties;
  private final boolean myAllowRemote;

  private final @NotNull TypeEvalContext myTypeEvalContext;

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
  @Deprecated(forRemoval = true)
  public static @NotNull PyResolveContext defaultContext() {
    return new PyResolveContext(false, true, false, TypeEvalContext.codeInsightFallback(null));
  }

  public static @NotNull PyResolveContext defaultContext(@NotNull TypeEvalContext context) {
    return new PyResolveContext(false, true, false, context);
  }

  /**
   * Allow searching for dynamic usages based on duck typing and guesses during resolve.
   *
   * Note that this resolve context is slower than the default one. Use it only for one-off user actions.
   */
  public static @NotNull PyResolveContext implicitContext(@NotNull TypeEvalContext context) {
    return new PyResolveContext(true, true, false, context);
  }

  public static @NotNull PyResolveContext noProperties(@NotNull TypeEvalContext context) {
    return new PyResolveContext(false, false, false, context);
  }

  public @NotNull PyResolveContext withTypeEvalContext(@NotNull TypeEvalContext context) {
    return new PyResolveContext(myAllowImplicits, myAllowProperties, myAllowRemote, context);
  }

  public @NotNull PyResolveContext withoutImplicits() {
    return allowImplicits() ? new PyResolveContext(false, myAllowProperties, myAllowRemote, myTypeEvalContext) : this;
  }

  public @NotNull PyResolveContext withRemote() {
    return allowRemote() ? this : new PyResolveContext(myAllowImplicits, myAllowProperties, true, myTypeEvalContext);
  }

  public @NotNull TypeEvalContext getTypeEvalContext() {
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
