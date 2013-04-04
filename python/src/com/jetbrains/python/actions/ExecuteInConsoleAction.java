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
import com.intellij.execution.console.LanguageConsoleViewImpl;
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
    Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
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

  private static void execute(AnActionEvent e, final String selectionText) {
    findCodeExecutor(e, new Consumer<PyCodeExecutor>() {
      @Override
      public void consume(PyCodeExecutor codeExecutor) {
        executeInConsole(codeExecutor, selectionText);
      }
    });
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

  private static String getTextToExecute(@NotNull Editor editor) {
    String text = getSelectionText(editor);
    if (text != null) {
      return text;
    }

    return getLineUnderCaret(editor);
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
    Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());

    boolean enabled = editor != null && !StringUtil.isEmpty(getTextToExecute(editor)) && isPython(editor);

    Presentation presentation = e.getPresentation();
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
        return dom.getExecutionConsole() instanceof PyCodeExecutor && isAlive(dom);
      }
    });
  }

  private static boolean isAlive(RunContentDescriptor dom) {
    ProcessHandler processHandler = dom.getProcessHandler();
    return processHandler != null && !processHandler.isProcessTerminated();
  }

  private static void findCodeExecutor(AnActionEvent e, Consumer<PyCodeExecutor> consumer) {
    Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project != null && editor != null) {
      if (canFindConsole(e)) {
        selectConsole(project, editor, consumer);
      }
      else {
        startConsole(project, consumer, e.getData(LangDataKeys.MODULE));
      }
    }
  }

  private static void startConsole(final Project project,
                                   final Consumer<PyCodeExecutor> consumer,
                                   Module context) {
    PydevConsoleRunner runner = RunPythonConsoleAction.runPythonConsole(project, context);
    runner.addConsoleListener(new PydevConsoleRunner.ConsoleListener() {
      @Override
      public void handleConsoleInitialized(LanguageConsoleViewImpl consoleView) {
        if (consoleView instanceof PyCodeExecutor) {
          consumer.consume((PyCodeExecutor)consoleView);
        }
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
