/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

public class EmptyRunnable implements Runnable {
  public static final Runnable INSTANCE = new EmptyRunnable();

  public static Runnable getInstance() {
    return INSTANCE;
  }

  public void run() {
  }
}
