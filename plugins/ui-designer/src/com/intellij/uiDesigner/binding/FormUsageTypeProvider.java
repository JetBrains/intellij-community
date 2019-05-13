// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.binding;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProvider;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class FormUsageTypeProvider implements UsageTypeProvider {
  @Override
  @Nullable
  public UsageType getUsageType(PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile != null && psiFile.getFileType() == StdFileTypes.GUI_DESIGNER_FORM) {
      return FORM_USAGE_TYPE;
    }
    return null;
  }

  private static final UsageType FORM_USAGE_TYPE = new UsageType(UIDesignerBundle.message("form.usage.type"));
}