/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.util.IncorrectOperationException;

import java.util.List;

/**
 * @author dsl
 */
public interface MatchProvider {
  void processMatch(Match match) throws IncorrectOperationException;

  List<Match> getDuplicates();

  boolean hasDuplicates();
}
