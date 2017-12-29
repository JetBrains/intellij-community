/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.emmet;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
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
      PsiFile file = project == null ? null : PsiUtilBase.getPsiFileInEditor(editor, project);
      if (file == null) return;

      EmmetPreviewHint existingBalloon = EmmetPreviewHint.getExistingHint(editor);
      if (existingBalloon == null) {
        String templateText = EmmetPreviewUtil.calculateTemplateText(editor, file, false);
        if (StringUtil.isNotEmpty(templateText)) {
          EmmetPreviewHint.createHint((EditorEx)editor, templateText, file.getFileType()).showHint();
          EmmetPreviewUtil.addEmmetPreviewListeners(editor, file, false);
        }
      }
    }
  }
}
