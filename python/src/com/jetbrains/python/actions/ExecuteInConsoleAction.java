/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.jetbrains.python.actions;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.console.PydevLanguageConsoleView;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExecuteInConsoleAction extends AnAction {
  public ExecuteInConsoleAction() {
    super("Execute selection in console");
  }

  public void actionPerformed(AnActionEvent e) {
    String selectionText = getSelectionText(e);
    if (selectionText != null) {
      executeInConsole(getConsole(e), selectionText);
    }
  }

  @Nullable
  private static String getSelectionText(AnActionEvent e) {
    Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
    if (editor != null) {
      SelectionModel model = editor.getSelectionModel();
      return model.getSelectedText();
    }
    return null;
  }

  public void update(AnActionEvent e) {
    boolean enabled = !StringUtil.isEmpty(getSelectionText(e)) && isPython(e) && getConsole(e) != null;

    Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  private static boolean isPython(AnActionEvent e) {
    Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());

    if (project == null || editor == null) {
      return false;
    }

    PsiFile psi = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    return psi instanceof PyFile;
  }

  @Nullable
  private static PydevLanguageConsoleView getConsole(@NotNull Project project) {
    RunContentDescriptor descriptor = ExecutionManager.getInstance(project).getContentManager().getSelectedContent();
    if (descriptor != null && descriptor.getExecutionConsole() instanceof PydevLanguageConsoleView) {
      return (PydevLanguageConsoleView)descriptor.getExecutionConsole();
    }
    return null;
  }

  @Nullable
  private static PydevLanguageConsoleView getConsole(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project != null) {
      return getConsole(project);
    }
    else {
      return null;
    }
  }

  private static void executeInConsole(@NotNull PydevLanguageConsoleView pydevConsole, @NotNull String text) {
    pydevConsole.executeMultiline(text);
  }
}
