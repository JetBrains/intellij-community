/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util;

/**
 * @author max
 */
public interface Function<Source, Target> {
  Target fun(Source s);
}
