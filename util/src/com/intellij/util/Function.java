/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util;

/**
 * @author max
 */
public interface Function<Dom, Img> {
  Img fun(Dom s);
}
