// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement insertRequiredAttributeFix(@NotNull XmlTag tag, @NotNull String attrName,
                                                                                         String @NotNull ... values);

  @NotNull
  public abstract LocalQuickFix createNSDeclarationIntentionFix(@NotNull final PsiElement element,
                                           @NotNull String namespacePrefix,
                                           @Nullable final XmlToken token);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement addAttributeValueFix(@NotNull XmlAttribute attribute);
}
