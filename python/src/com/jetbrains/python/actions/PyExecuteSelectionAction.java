/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullFunction;
import com.jetbrains.python.console.PyCodeExecutor;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.console.PythonConsoleRunnerFactory;
import com.jetbrains.python.console.PythonConsoleToolWindow;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class PyExecuteSelectionAction extends AnAction {

  public static final String EXECUTE_SELECTION_IN_CONSOLE = "Execute Selection in Console";

  public PyExecuteSelectionAction() {
    super(EXECUTE_SELECTION_IN_CONSOLE);
  }

  public void actionPerformed(AnActionEvent e) {
    Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor != null) {
      final String selectionText = getSelectionText(editor);
      if (selectionText != null) {
        execute(e, selectionText);
      }
      else {
        String line = getLineUnderCaret(editor);
        if (line != null) {
          execute(e, line);
          moveCaretDown(editor);
        }
      }
    }
  }

  private static void moveCaretDown(Editor editor) {
    VisualPosition pos = editor.getCaretModel().getVisualPosition();
    Pair<LogicalPosition, LogicalPosition> lines = EditorUtil.calcSurroundingRange(editor, pos, pos);
    int offset = editor.getCaretModel().getOffset();

    LogicalPosition lineStart = lines.first;
    LogicalPosition nextLineStart = lines.second;

    int start = editor.logicalPositionToOffset(lineStart);
    int end = editor.logicalPositionToOffset(nextLineStart);

    Document document = editor.getDocument();

    if (nextLineStart.line < document.getLineCount()) {

      int newOffset = end + offset - start;

      int nextLineEndOffset = document.getLineEndOffset(nextLineStart.line);
      if (newOffset >= nextLineEndOffset) {
        newOffset = nextLineEndOffset;
      }

      editor.getCaretModel().moveToOffset(newOffset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  private static void execute(final AnActionEvent e, final String selectionText) {
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    Module module = e.getData(LangDataKeys.MODULE);

    findCodeExecutor(e, new Consumer<PyCodeExecutor>() {
      @Override
      public void consume(PyCodeExecutor codeExecutor) {
        executeInConsole(codeExecutor, selectionText, editor);
      }
    }, editor, project, module);
  }

  private static String getLineUnderCaret(Editor editor) {
    VisualPosition caretPos = editor.getCaretModel().getVisualPosition();

    Pair<LogicalPosition, LogicalPosition> lines = EditorUtil.calcSurroundingRange(editor, caretPos, caretPos);

    LogicalPosition lineStart = lines.first;
    LogicalPosition nextLineStart = lines.second;
    int start = editor.logicalPositionToOffset(lineStart);
    int end = editor.logicalPositionToOffset(nextLineStart);
    if (end <= start) {
      return null;
    }
    return editor.getDocument().getCharsSequence().subSequence(start, end).toString();
  }

  @Nullable
  private static String getSelectionText(@NotNull Editor editor) {
    if (editor.getSelectionModel().hasSelection()) {
      SelectionModel model = editor.getSelectionModel();

      return model.getSelectedText();
    }
    else {
      return null;
    }
  }

  public void update(AnActionEvent e) {
    Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    Presentation presentation = e.getPresentation();

    boolean enabled = false;
    if (editor != null && isPython(editor)) {
      String text = getSelectionText(editor);
      if (text != null) {
        presentation.setText(EXECUTE_SELECTION_IN_CONSOLE);
      }
      else {
        text = getLineUnderCaret(editor);
        if (text != null) {
          presentation.setText("Execute Line in Console");
        }
      }

      enabled = !StringUtil.isEmpty(text);
    }

    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  private static boolean isPython(Editor editor) {
    if (editor == null) {
      return false;
    }

    Project project = editor.getProject();

    if (project == null) {
      return false;
    }

    PsiFile psi = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    return psi instanceof PyFile;
  }

  private static void selectConsole(@NotNull DataContext dataContext, @NotNull Project project,
                                    final Consumer<PyCodeExecutor> consumer) {
    Collection<RunContentDescriptor> consoles = getConsoles(project);

    ExecutionHelper
      .selectContentDescriptor(dataContext, project, consoles, "Select console to execute in", new Consumer<RunContentDescriptor>() {
        @Override
        public void consume(RunContentDescriptor descriptor) {
          if (descriptor != null && descriptor.getExecutionConsole() instanceof PyCodeExecutor) {
            consumer.consume((PyCodeExecutor)descriptor.getExecutionConsole());
          }
        }
      });
  }

  private static Collection<RunContentDescriptor> getConsoles(Project project) {
    PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(project);

    if (toolWindow != null && toolWindow.getToolWindow().isVisible()) {
      RunContentDescriptor selectedContentDescriptor = toolWindow.getSelectedContentDescriptor();
      return selectedContentDescriptor != null ? Lists.newArrayList(selectedContentDescriptor) : Lists.<RunContentDescriptor>newArrayList();
    }

    Collection<RunContentDescriptor> descriptors =
      ExecutionHelper.findRunningConsole(project, new NotNullFunction<RunContentDescriptor, Boolean>() {
        @NotNull
        @Override
        public Boolean fun(RunContentDescriptor dom) {
          return dom.getExecutionConsole() instanceof PyCodeExecutor && isAlive(dom);
        }
      });

    if (descriptors.isEmpty() && toolWindow != null) {
      return toolWindow.getConsoleContentDescriptors();
    }
    else {
      return descriptors;
    }
  }

  private static boolean isAlive(RunContentDescriptor dom) {
    ProcessHandler processHandler = dom.getProcessHandler();
    return processHandler != null && !processHandler.isProcessTerminated();
  }

  private static void findCodeExecutor(AnActionEvent e, Consumer<PyCodeExecutor> consumer, Editor editor, Project project, Module module) {
    if (project != null && editor != null) {
      if (canFindConsole(e)) {
        selectConsole(e.getDataContext(), project, consumer);
      }
      else {
        startConsole(project, consumer, module);
      }
    }
  }

  private static void startConsole(final Project project,
                                   final Consumer<PyCodeExecutor> consumer,
                                   Module context) {
    final PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(project);

    if (toolWindow != null) {
      toolWindow.activate(new Runnable() {
        @Override
        public void run() {
          List<RunContentDescriptor> descs = toolWindow.getConsoleContentDescriptors();

          RunContentDescriptor descriptor = descs.get(0);
          if (descriptor != null && descriptor.getExecutionConsole() instanceof PyCodeExecutor) {
            consumer.consume((PyCodeExecutor)descriptor.getExecutionConsole());
          }
        }
      });
    }
    else {
      PythonConsoleRunnerFactory consoleRunnerFactory = PythonConsoleRunnerFactory.getInstance();
      PydevConsoleRunner runner = consoleRunnerFactory.createConsoleRunner(project, null);
      runner.addConsoleListener(new PydevConsoleRunner.ConsoleListener() {
        @Override
        public void handleConsoleInitialized(LanguageConsoleView consoleView) {
          if (consoleView instanceof PyCodeExecutor) {
            consumer.consume((PyCodeExecutor)consoleView);
          }
        }
      });
      runner.run();
    }
  }

  private static boolean canFindConsole(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project != null) {
      Collection<RunContentDescriptor> descriptors = getConsoles(project);
      return descriptors.size() > 0;
    }
    else {
      return false;
    }
  }

  private static void executeInConsole(@NotNull PyCodeExecutor codeExecutor, @NotNull String text, Editor editor) {
    codeExecutor.executeCode(text, editor);
  }
}
