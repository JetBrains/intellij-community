// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddAttributeValueIntentionFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public AddAttributeValueIntentionFix(@Nullable PsiElement element) {
    super(element);
  }

  @Override
  public @NotNull String getText() {
    return XmlPsiBundle.message("xml.quickfix.add.attribute.value.text");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getName();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     final @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final XmlAttribute attribute = PsiTreeUtil.getNonStrictParentOfType(startElement, XmlAttribute.class);
    if (attribute == null || attribute.getValue() != null) {
      return;
    }

    final XmlAttribute attributeWithValue = XmlElementFactory.getInstance(project).createAttribute(attribute.getName(), "", startElement);
    final PsiElement newAttribute = attribute.replace(attributeWithValue);

    if (editor != null && newAttribute instanceof XmlAttribute && newAttribute.isValid()) {
      final XmlAttributeValue valueElement = ((XmlAttribute)newAttribute).getValueElement();
      if (valueElement != null) {
        editor.getCaretModel().moveToOffset(valueElement.getTextOffset());
        if (!IntentionPreviewUtils.isIntentionPreviewActive()) {
          AutoPopupController.getInstance(newAttribute.getProject()).scheduleAutoPopup(editor);
        }
      }
    }
  }
}
