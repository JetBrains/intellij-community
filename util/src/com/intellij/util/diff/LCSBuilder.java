/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.diff;

/**
 * @author dyoma
 */
interface LCSBuilder {
  void addEqual(int length);
  void addChange(int first, int second);
}
