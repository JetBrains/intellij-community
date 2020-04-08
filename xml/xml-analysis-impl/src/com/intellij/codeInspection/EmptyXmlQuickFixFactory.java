/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.QuickFixes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmptyXmlQuickFixFactory extends XmlQuickFixFactory {
  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement insertRequiredAttributeFix(@NotNull XmlTag tag,
                                                                                @NotNull String attrName,
                                                                                String @NotNull ... values) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFix createNSDeclarationIntentionFix(@NotNull PsiElement element,
                                                       @NotNull String namespacePrefix,
                                                       @Nullable XmlToken token) {
    return QuickFixes.EMPTY_ACTION;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement addAttributeValueFix(@NotNull XmlAttribute attribute) {
    return QuickFixes.EMPTY_FIX;
  }
}
