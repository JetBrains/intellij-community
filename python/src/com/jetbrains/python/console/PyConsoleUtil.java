// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.google.common.base.CharMatcher;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IJSwingUtilities;
import com.jetbrains.python.console.actions.CommandQueueForPythonConsoleService;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@ApiStatus.Internal
public final class PyConsoleUtil {
  public static final String ORDINARY_PROMPT = ">>>";
  public static final String INPUT_PROMPT = ">?";
  public static final String INDENT_PROMPT = "...";
  public static final String IPYTHON_INDENT_PROMPT = "...:";

  public static final String HELP_PROMPT = "help>";
  public static final String EXECUTING_PROMPT = "";

  private static final String IPYTHON_PAGING_PROMPT = "---Return to continue, q to quit---";

  public static final @NonNls String ASYNCIO_REPL_ENV = "ASYNCIO_REPL";


  public static final Key<PythonConsoleData> PYTHON_CONSOLE_DATA = Key.create("python-console-data");

  private PyConsoleUtil() {
  }

  public static boolean isPagingPrompt(@Nullable String prompt) {
    return prompt != null && IPYTHON_PAGING_PROMPT.equals(prompt.trim());
  }


  public static void scrollDown(final Editor currentEditor) {
    ApplicationManager.getApplication().invokeLater(
      () -> {
        if (!currentEditor.isDisposed()) currentEditor.getCaretModel().moveToOffset(currentEditor.getDocument().getTextLength());
      }
    );
  }


  public static boolean detectIPythonImported(@NotNull String text) {
    return text.contains("PyDev console: using IPython ");
  }

  public static boolean detectSourcePrinting(@NotNull String text) {
    return text.contains("Source:");
  }

  public static boolean detectIPythonStart(@NotNull String text) {
    return text.contains("IPython-->");
  }

  public static boolean detectIPythonEnd(@NotNull String text) {
    return text.contains("<--IPython");
  }

  public static boolean detectIPythonAutomagicOn(@NotNull String text) {
    return text.contains("Automagic is ON, % prefix NOT needed for magic functions.");
  }

  public static boolean detectIPythonAutomagicOff(@NotNull String text) {
    return text.contains("Automagic is OFF, % prefix IS needed for magic functions.");
  }

  public static void markIPython(@NotNull VirtualFile file) {
    PythonConsoleData consoleData = getOrCreateIPythonData(file);
    consoleData.setIPythonEnabled(true);
  }

  public static @NotNull PythonConsoleData getOrCreateIPythonData(@NotNull VirtualFile file) {
    PythonConsoleData consoleData = file.getUserData(PYTHON_CONSOLE_DATA);
    if (consoleData == null) {
      consoleData = new PythonConsoleData();
      file.putUserData(PYTHON_CONSOLE_DATA, consoleData);
    }
    return consoleData;
  }

  public static void setIPythonAutomagic(@NotNull VirtualFile file, boolean detected) {
    PythonConsoleData consoleData = getOrCreateIPythonData(file);
    consoleData.setIPythonAutomagic(detected);
  }

  public static AnAction createTabCompletionAction(PythonConsoleView consoleView) {
    final AnAction runCompletions = new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = consoleView.getConsoleEditor();
        if (LookupManager.getActiveLookup(editor) != null) {
          AnAction replace = ActionManager.getInstance().getAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE);
          ActionUtil.performAction(replace, e);
          return;
        }
        AnAction completionAction = ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION);
        if (completionAction != null) {
          ActionUtil.performAction(completionAction, e);
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setVisible(false);
        Editor editor = consoleView.getConsoleEditor();
        if (LookupManager.getActiveLookup(editor) != null) {
          e.getPresentation().setEnabled(false);
        }
        int offset = editor.getCaretModel().getOffset();
        Document document = editor.getDocument();
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
        String textToCursor = document.getText(new TextRange(lineStart, offset));
        e.getPresentation().setEnabled(!CharMatcher.whitespace().matchesAllOf(textToCursor));
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    };

    runCompletions.registerCustomShortcutSet(KeyEvent.VK_TAB, 0, consoleView.getConsoleEditor().getComponent());
    return runCompletions;
  }

  public static AnAction createInterruptAction(PythonConsoleView consoleView) {
    AnAction anAction = new AnAction() {
      @Override
      public void actionPerformed(final @NotNull AnActionEvent e) {
        ConsoleCommunication consoleCommunication = consoleView.getExecuteActionHandler().getConsoleCommunication();
        if (consoleCommunication.isExecuting() || consoleCommunication.isWaitingForInput()) {
          consoleView.print("^C", ProcessOutputTypes.SYSTEM);
          consoleCommunication.interrupt();
        }
        else {
          DocumentEx document = consoleView.getConsoleEditor().getDocument();
          if (document.getTextLength() != 0) {
            ApplicationManager.getApplication().runWriteAction(() ->
                                                                 CommandProcessor
                                                                   .getInstance()
                                                                   .runUndoTransparentAction(() -> document.deleteString(0, document
                                                                     .getLineEndOffset(document.getLineCount() - 1))));
          }
        }
      }

      @Override
      public void update(final @NotNull AnActionEvent e) {
        e.getPresentation().setVisible(false);
        boolean enabled = false;
        EditorEx consoleEditor = consoleView.getConsoleEditor();
        if (IJSwingUtilities.hasFocus(consoleEditor.getComponent())) {
          enabled = !consoleEditor.getSelectionModel().hasSelection();
        }
        EditorEx historyViewer = consoleView.getHistoryViewer();
        if (IJSwingUtilities.hasFocus(historyViewer.getComponent())) {
          enabled = !historyViewer.getSelectionModel().hasSelection();
        }
        e.getPresentation().setEnabled(enabled);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    };

    anAction.registerCustomShortcutSet(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, consoleView.getConsoleEditor().getComponent());
    anAction.registerCustomShortcutSet(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, consoleView.getHistoryViewer().getComponent());
    return anAction;
  }

  public static boolean isCommandQueueEnabled(Project project) {
    return PyConsoleOptions.getInstance(project).isCommandQueueEnabled();
  }

  public static boolean isCommandQueueEmpty(@Nullable ConsoleCommunication communication) {
    if (communication != null) {
      return ApplicationManager.getApplication().getService(CommandQueueForPythonConsoleService.class).isEmpty(communication);
    }
    return true;
  }
}


