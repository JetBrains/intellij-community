/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.actions.context;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.tasks.actions.BaseTaskAction;
import com.intellij.tasks.context.WorkingContextManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

/**
 * @author Dmitry Avdeev
 */
public class SaveContextAction extends BaseTaskAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = getProject(e);
    saveContext(project);
  }

  public static void saveContext(Project project) {

    String initial = null;
    Editor textEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (textEditor != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(textEditor.getDocument());
      if (file != null) {
        initial = file.getName();
      }
    }
    String comment = Messages.showInputDialog(project, "Enter comment (optional):", "Save Context", null, initial, null);
    if (comment != null) {
      WorkingContextManager.getInstance(project).saveContext(null, StringUtil.isEmpty(comment) ? null : comment);
    }
  }
}
