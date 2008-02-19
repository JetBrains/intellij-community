/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.util.NotNullFunction;

/**
 * @author peter
 */
public class NotNullLazyKey<T,H extends UserDataHolder> extends Key<T>{
  private final NotNullFunction<H,T> myFunction;

  private NotNullLazyKey(@NonNls String name, final NotNullFunction<H, T> function) {
    super(name);
    myFunction = function;
  }

  @NotNull 
  public final T getValue(H h) {
    T data = h.getUserData(this);
    if (data == null) {
      h.putUserData(this, data = myFunction.fun(h));
    }
    return data;
  }

  public static <T,H extends UserDataHolder> NotNullLazyKey<T,H> create(@NonNls String name, final NotNullFunction<H, T> function) {
    return new NotNullLazyKey<T,H>(name, function);
  }
}
