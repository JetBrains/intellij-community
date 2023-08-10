// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.structuralsearch;

import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;

/**
 * @author Bas Leijdekkers
 */
class PropertiesMatchingStrategy implements MatchingStrategy {

  public static final PropertiesMatchingStrategy INSTANCE = new PropertiesMatchingStrategy();

  @Override
  public boolean continueMatching(PsiElement start) {
    return start.getLanguage() == PropertiesLanguage.INSTANCE;
  }

  @Override
  public boolean shouldSkip(PsiElement element, PsiElement elementToMatchWith) {
    return false;
  }
}
