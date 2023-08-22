// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.emmet;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.WrapWithCustomTemplateAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.xml.XmlBundle;
import java.util.HashSet;
import org.jetbrains.annotations.NotNull;

public class SurroundWithEmmetAction extends BaseCodeInsightAction {
  public SurroundWithEmmetAction() {
    setEnabledInModalContext(true);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return EmmetOptions.getInstance().isEmmetEnabled() &&
           TemplateManagerImpl.isApplicable(new ZenCodingTemplate(), TemplateActionContext.surrounding(file, editor));
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new SurroundWithEmmetHandler();
  }

  private static class SurroundWithEmmetHandler implements CodeInsightActionHandler {
    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
      SelectionModel selectionModel = editor.getSelectionModel();
      if (!selectionModel.hasSelection()) {
        SurroundWithHandler.selectLogicalLineContentsAtCaret(editor);
      }

      ZenCodingTemplate emmetCustomTemplate = CustomLiveTemplate.EP_NAME.findExtension(ZenCodingTemplate.class);
      if (emmetCustomTemplate != null) {
        new WrapWithCustomTemplateAction(emmetCustomTemplate, editor, file, new HashSet<>()).perform();
      }
      else if (!ApplicationManager.getApplication().isUnitTestMode()) {
        HintManager.getInstance().showErrorHint(editor, XmlBundle.message("emmet.action.surround.error.hint"));
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }
}
