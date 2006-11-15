/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface NullableFunction<Dom, Img> extends Function<Dom,Img> {
  @Nullable
  Img fun(final Dom dom);
}
