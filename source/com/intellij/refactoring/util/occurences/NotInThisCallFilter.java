/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.occurences;

/**
 * @author dsl
 */
public class NotInThisCallFilter extends NotInSuperOrThisCallFilterBase {
  public static final NotInThisCallFilter INSTANCE = new NotInThisCallFilter();
  protected String getKeywordText() {
    return "this";
  }
}
