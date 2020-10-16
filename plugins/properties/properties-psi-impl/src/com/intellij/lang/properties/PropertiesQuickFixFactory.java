// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;

import java.util.List;

public abstract class PropertiesQuickFixFactory {
  public static PropertiesQuickFixFactory getInstance() {
    return ApplicationManager.getApplication().getService(PropertiesQuickFixFactory.class);
  }

  public abstract LocalQuickFix createCreatePropertyFix(PsiElement element, String key, List<PropertiesFile> files);

  public abstract IntentionAction createRemovePropertyFix(Property property);

  public abstract LocalQuickFix createRemovePropertyLocalFix();
}
