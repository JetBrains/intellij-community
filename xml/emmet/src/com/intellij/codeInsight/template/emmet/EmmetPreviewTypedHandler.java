// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmmetPreviewTypedHandler extends TypedActionHandlerBase {
  public EmmetPreviewTypedHandler(@Nullable TypedActionHandler originalHandler) {
    super(originalHandler);
  }

  @Override
  public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    if (myOriginalHandler != null) myOriginalHandler.execute(editor, charTyped, dataContext);
    if (EmmetOptions.getInstance().isEmmetEnabled() && EmmetOptions.getInstance().isPreviewEnabled()) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project == null) return;
      PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
      if (file == null) return;

      EmmetPreviewHint existingBalloon = EmmetPreviewHint.getExistingHint(editor);
      if (existingBalloon != null) return;

      PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
      ReadAction.nonBlocking(() -> {
          if (editor.isDisposed() || EmmetPreviewHint.getExistingHint(editor) != null) return null;
          return EmmetPreviewUtil.calculateTemplateText(editor, file, true);
        })
        .finishOnUiThread(ModalityState.current(), templateText -> {
          if (StringUtil.isNotEmpty(templateText)) {
            EmmetPreviewHint.createHint((EditorEx)editor, templateText, file.getFileType()).showHint();
            EmmetPreviewUtil.addEmmetPreviewListeners(editor, file, false);
          }
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    }
  }
}
