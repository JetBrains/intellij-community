/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

public class LocalTimeCounter {
  private static long ourCurrentTime = 0;

  public static long currentTime() {
    return ++ourCurrentTime;
  }
}