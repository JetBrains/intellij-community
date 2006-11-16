/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public interface PairFunction<T, V, U> {
  @Nullable
  U fun(T t, V v);

}
