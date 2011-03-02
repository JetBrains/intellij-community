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

import com.intellij.execution.ExecutionHelper;
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
import com.intellij.util.Consumer;
import com.intellij.util.NotNullFunction;
import com.jetbrains.python.console.PydevLanguageConsoleView;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ExecuteInConsoleAction extends AnAction {
  public ExecuteInConsoleAction() {
    super("Execute selection in console");
  }

  public void actionPerformed(AnActionEvent e) {
    final String selectionText = getSelectionText(e);
    if (selectionText != null) {
      selectConsole(e, new Consumer<PydevLanguageConsoleView>() {
        @Override
        public void consume(PydevLanguageConsoleView pydevLanguageConsole) {
          executeInConsole(pydevLanguageConsole, selectionText);
        }
      });
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
    boolean enabled = !StringUtil.isEmpty(getSelectionText(e)) && isPython(e) && canFindConsole(e);

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
  private static PydevLanguageConsoleView selectConsole(@NotNull Project project,
                                                        @NotNull Editor editor,
                                                        final Consumer<PydevLanguageConsoleView> consumer) {
    Collection<RunContentDescriptor> consoles = getConsoles(project);

    ExecutionHelper.selectContentDescriptor(editor, consoles, "Select console to execute in", new Consumer<RunContentDescriptor>() {
      @Override
      public void consume(RunContentDescriptor descriptor) {
        if (descriptor != null && descriptor.getExecutionConsole() instanceof PydevLanguageConsoleView) {
          consumer.consume((PydevLanguageConsoleView)descriptor.getExecutionConsole());
        }
      }
    });

    return null;
  }

  private static Collection<RunContentDescriptor> getConsoles(Project project) {
    return ExecutionHelper.findRunningConsoleByTitle(project, new NotNullFunction<String, Boolean>() {
      @NotNull
      @Override
      public Boolean fun(String dom) {
        return dom.contains("Python") || dom.contains("Django");
      }
    });
  }

  @Nullable
  private static PydevLanguageConsoleView selectConsole(AnActionEvent e, Consumer<PydevLanguageConsoleView> consumer) {
    Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project != null && editor != null) {
      return selectConsole(project, editor, consumer);
    }
    else {
      return null;
    }
  }

  private static boolean canFindConsole(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project != null) {
      Collection<RunContentDescriptor> descriptors = getConsoles(project);
      return descriptors.size() > 0;
    }
    else {
      return false;
    }
  }

  private static void executeInConsole(@NotNull PydevLanguageConsoleView pydevConsole, @NotNull String text) {
    pydevConsole.executeMultiline(text);
  }
}
