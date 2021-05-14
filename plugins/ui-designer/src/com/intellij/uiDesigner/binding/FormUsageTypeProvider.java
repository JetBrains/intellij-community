// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.binding;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FormUsageTypeProvider implements UsageTypeProvider {
  @Override
  @Nullable
  public UsageType getUsageType(@NotNull PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile != null && psiFile.getFileType() == GuiFormFileType.INSTANCE) {
      return FORM_USAGE_TYPE;
    }
    return null;
  }

  private static final UsageType FORM_USAGE_TYPE = new UsageType(UIDesignerBundle.messagePointer("form.usage.type"));
}