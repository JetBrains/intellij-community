/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.binding;

import com.intellij.usages.impl.rules.UsageTypeProvider;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class FormUsageTypeProvider implements UsageTypeProvider {
  @Nullable
  public UsageType getUsageType(PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile.getFileType() == StdFileTypes.GUI_DESIGNER_FORM) {
      return FORM_USAGE_TYPE;
    }
    return null;
  }

  private static final UsageType FORM_USAGE_TYPE = new UsageType(UIDesignerBundle.message("form.usage.type"));
}