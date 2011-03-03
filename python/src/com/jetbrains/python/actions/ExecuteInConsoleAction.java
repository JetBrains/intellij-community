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
import com.jetbrains.python.console.PyCodeExecutor;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.console.RunPythonConsoleAction;
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
      findCodeExecutor(e, new Consumer<PyCodeExecutor>() {
        @Override
        public void consume(PyCodeExecutor codeExecutor) {
          executeInConsole(codeExecutor, selectionText);
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
    boolean enabled = !StringUtil.isEmpty(getSelectionText(e)) && isPython(e);

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

  private static void selectConsole(@NotNull Project project,
                                    @NotNull Editor editor,
                                    final Consumer<PyCodeExecutor> consumer) {
    Collection<RunContentDescriptor> consoles = getConsoles(project);

    ExecutionHelper.selectContentDescriptor(editor, consoles, "Select console to execute in", new Consumer<RunContentDescriptor>() {
      @Override
      public void consume(RunContentDescriptor descriptor) {
        if (descriptor != null && descriptor.getExecutionConsole() instanceof PyCodeExecutor) {
          consumer.consume((PyCodeExecutor)descriptor.getExecutionConsole());
        }
      }
    });
  }

  private static Collection<RunContentDescriptor> getConsoles(Project project) {
    return ExecutionHelper.findRunningConsole(project, new NotNullFunction<RunContentDescriptor, Boolean>() {
      @NotNull
      @Override
      public Boolean fun(RunContentDescriptor dom) {
        return dom.getExecutionConsole() instanceof PyCodeExecutor;
      }
    });
  }

  private static void findCodeExecutor(AnActionEvent e, Consumer<PyCodeExecutor> consumer) {
    Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project != null && editor != null) {
      if (canFindConsole(e)) {
        selectConsole(project, editor, consumer);
      }
      else {
        startConsole(project, editor, consumer);
      }
    }
  }

  private static void startConsole(final Project project, final Editor editor, final Consumer<PyCodeExecutor> consumer) {
    PydevConsoleRunner runner = RunPythonConsoleAction.runPythonConsole(project);
    assert runner != null;
    runner.addConsoleListener(new PydevConsoleRunner.ConsoleListener() {
      @Override
      public void handleConsoleInitialized() {
        selectConsole(project, editor, consumer);
      }
    });

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

  private static void executeInConsole(@NotNull PyCodeExecutor codeExecutor, @NotNull String text) {
    codeExecutor.executeCode(text);
  }
}
