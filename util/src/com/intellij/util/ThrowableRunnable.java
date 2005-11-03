/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util;

/**
 * @author mike
 */
public interface ThrowableRunnable<T extends Throwable> {
  void run() throws T;
}
