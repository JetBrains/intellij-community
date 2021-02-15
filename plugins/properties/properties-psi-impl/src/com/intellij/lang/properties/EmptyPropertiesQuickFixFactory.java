// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixes;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;

import java.util.List;

final class EmptyPropertiesQuickFixFactory extends PropertiesQuickFixFactory {
  @Override
  public LocalQuickFix createCreatePropertyFix(PsiElement element, String key, List<PropertiesFile> files) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public IntentionAction createRemovePropertyFix(Property property) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Override
  public LocalQuickFix createRemovePropertyLocalFix() {
    return QuickFixes.EMPTY_ACTION;
  }
}
