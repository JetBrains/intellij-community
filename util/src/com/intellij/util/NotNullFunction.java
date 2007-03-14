/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface NotNullFunction<Dom, Img> extends Function<Dom,Img> {
  NotNullFunction ID = new NotNullFunction() {
    @NotNull
    public Object fun(final Object o) {
      return o;
    }
  };

  @NotNull
  Img fun(final Dom dom);
}
