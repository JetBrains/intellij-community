/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.emmet.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

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
    if (!EmmetEditPointUtil.isApplicableFile(file)) {
      presentation.setEnabledAndVisible(false);
    }
  }

  private static PsiFile getFile(DataContext context) {
    return CommonDataKeys.PSI_FILE.getData(context);
  }

  public static class Forward extends GoToEditPointAction {
    public Forward() {
      super(new EditorActionHandler(true) {
        @Override
        protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
          EmmetEditPointUtil.moveForward(editor, getFile(dataContext));
        }
      });
    }
  }

  public static class Backward extends GoToEditPointAction {
    public Backward() {
      super(new EditorActionHandler(true) {
        @Override
        protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
          EmmetEditPointUtil.moveBackward(editor, getFile(dataContext));
        }
      });
    }
  }
}
