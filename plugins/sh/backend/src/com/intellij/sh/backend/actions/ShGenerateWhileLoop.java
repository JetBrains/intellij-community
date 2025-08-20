// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class ShGenerateWhileLoop extends ShBaseGenerateAction {
  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return this;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    TemplateManager templateManager = TemplateManager.getInstance(project);
    Template template = TemplateSettings.getInstance().getTemplateById("shell_while");
    if (template == null) return;

    moveAtNewLineIfNeeded(editor);
    templateManager.startTemplate(editor, template);
  }
}
