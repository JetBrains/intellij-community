// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XmlQuickFixFactory {
  public static XmlQuickFixFactory getInstance() {
    return ApplicationManager.getApplication().getService(XmlQuickFixFactory.class);
  }

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement insertRequiredAttributeFix(@NotNull XmlTag tag, @NotNull String attrName,
                                                                                                  String @NotNull ... values);

  public abstract @NotNull LocalQuickFix createNSDeclarationIntentionFix(final @NotNull PsiElement element,
                                                                         @NotNull String namespacePrefix,
                                                                         final @Nullable XmlToken token);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement addAttributeValueFix(@NotNull XmlAttribute attribute);
}
