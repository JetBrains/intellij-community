// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.CreateNSDeclarationIntentionFix;
import com.intellij.codeInsight.daemon.impl.analysis.InsertRequiredAttributeFix;
import com.intellij.codeInspection.htmlInspections.AddAttributeValueIntentionFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlQuickFixFactoryImpl extends XmlQuickFixFactory {
  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement insertRequiredAttributeFix(@NotNull XmlTag tag, @NotNull String attrName, String @NotNull ... values) {
    return new InsertRequiredAttributeFix(tag, attrName, values);
  }

  @Override
  public @NotNull LocalQuickFix createNSDeclarationIntentionFix(@NotNull PsiElement element, @NotNull String namespacePrefix, @Nullable XmlToken token) {
    return new CreateNSDeclarationIntentionFix(element, namespacePrefix, token);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement addAttributeValueFix(@NotNull XmlAttribute attribute) {
    return new AddAttributeValueIntentionFix(attribute);
  }
}
