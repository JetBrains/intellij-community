// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public final class PyResolveContext {
  private final boolean myAllowImplicits;
  private final boolean myAllowProperties;
  private final boolean myAllowRemote;
  private final TypeEvalContext myTypeEvalContext;


  private PyResolveContext(boolean allowImplicits, boolean allowProperties) {
    myAllowImplicits = allowImplicits;
    myAllowProperties = allowProperties;
    myTypeEvalContext = null;
    myAllowRemote = false;
  }


  private PyResolveContext(boolean allowImplicits, boolean allowProperties, boolean allowRemote, TypeEvalContext typeEvalContext) {
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

  private static final PyResolveContext ourDefaultContext = new PyResolveContext(false, true);
  private static final PyResolveContext ourImplicitsContext = new PyResolveContext(true, true);
  private static final PyResolveContext ourNoPropertiesContext = new PyResolveContext(false, false);

  public static PyResolveContext defaultContext() {
    return ourDefaultContext;
  }

  /**
   * Allow searching for dynamic usages based on duck typing and guesses during resolve.
   *
   * Note that this resolve context is slower than the default one. Use it only for one-off user actions.
   */
  @NotNull
  public static PyResolveContext implicitContext() {
    return ourImplicitsContext;
  }

  /**
   * @deprecated Use {@link #defaultContext()} instead, now it doesn't contain implicit results.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static PyResolveContext noImplicits() {
    Logger.getInstance(PyResolveContext.class).warn("Deprecated method used: 'noImplicits'. This method will be dropped soon." +
                                                    "Consider migrate to the new one");
    return defaultContext();
  }

  public static PyResolveContext noProperties() {
    return ourNoPropertiesContext;
  }

  public PyResolveContext withTypeEvalContext(@NotNull TypeEvalContext context) {
    return new PyResolveContext(myAllowImplicits, myAllowProperties, myAllowRemote, context);
  }

  public PyResolveContext withoutImplicits() {
    return new PyResolveContext(false, myAllowProperties, myAllowRemote, myTypeEvalContext);
  }

  public PyResolveContext withRemote() {
    return new PyResolveContext(myAllowImplicits, myAllowProperties, true, myTypeEvalContext);
  }

  public TypeEvalContext getTypeEvalContext() {
    return myTypeEvalContext != null ? myTypeEvalContext : TypeEvalContext.codeInsightFallback(null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyResolveContext that = (PyResolveContext)o;

    if (myAllowImplicits != that.myAllowImplicits) return false;
    if (myTypeEvalContext != null ? !myTypeEvalContext.equals(that.myTypeEvalContext) : that.myTypeEvalContext != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myAllowImplicits ? 1 : 0);
    result = 31 * result + (myTypeEvalContext != null ? myTypeEvalContext.hashCode() : 0);
    return result;
  }
}
