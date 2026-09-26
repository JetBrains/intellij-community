// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.actions;

import com.intellij.codeInsight.editorActions.XmlGtTypedHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public abstract class GoToEditPointAction extends EditorAction implements DumbAware {
  protected GoToEditPointAction(EditorActionHandler defaultHandler) {
    super(defaultHandler);
  }

  @Override
  public void update(Editor editor, Presentation presentation, DataContext dataContext) {
    super.update(editor, presentation, dataContext);
    final PsiFile file = getFile(dataContext);
    if (!XmlGtTypedHandler.fileContainsXmlLanguage(file)) {
      presentation.setEnabledAndVisible(false);
    }
  }

  private static PsiFile getFile(DataContext context) {
    return CommonDataKeys.PSI_FILE.getData(context);
  }

  public static class Forward extends GoToEditPointAction {
    public Forward() {
      super(new EditorActionHandler.ForEachCaret() {
        @Override
        protected void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
          EmmetEditPointUtil.moveForward(editor, getFile(dataContext));
        }
      });
    }
  }

  public static class Backward extends GoToEditPointAction {
    public Backward() {
      super(new EditorActionHandler.ForEachCaret() {
        @Override
        protected void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
          EmmetEditPointUtil.moveBackward(editor, getFile(dataContext));
        }
      });
    }
  }
}
