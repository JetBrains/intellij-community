/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

/**
 *  @author dsl
 */
public interface Computable <T> {
  T compute();
}
