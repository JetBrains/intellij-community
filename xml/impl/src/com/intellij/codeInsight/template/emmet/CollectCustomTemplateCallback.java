// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingListener;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class CollectCustomTemplateCallback extends CustomTemplateCallback {
  private @Nullable TemplateImpl myTemplate;

  public CollectCustomTemplateCallback(@NotNull Editor editor, @NotNull PsiFile file) {
    super(editor, file);
  }

  @Override
  public void deleteTemplateKey(@NotNull String key) {
  }

  @Override
  public void startTemplate(@NotNull Template template, Map<String, String> predefinedValues, TemplateEditingListener listener) {
    if (template instanceof TemplateImpl && !((TemplateImpl)template).isDeactivated()) {
      myTemplate = (TemplateImpl)template;
    }
  }
  
  public @Nullable TemplateImpl getGeneratedTemplate() {
    return myTemplate;
  }
}
