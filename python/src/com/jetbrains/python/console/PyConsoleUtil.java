// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.google.common.base.CharMatcher;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IJSwingUtilities;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public final class PyConsoleUtil {
  public static final String ORDINARY_PROMPT = ">>>";
  public static final String INPUT_PROMPT = ">?";
  public static final String INDENT_PROMPT = "...";
  public static final String IPYTHON_INDENT_PROMPT = "...:";

  public static final String HELP_PROMPT = "help>";
  public static final String EXECUTING_PROMPT = "";

  private static final String IPYTHON_PAGING_PROMPT = "---Return to continue, q to quit---";

  private static final String[] PROMPTS = new String[]{
    ORDINARY_PROMPT,
    INDENT_PROMPT,
    HELP_PROMPT,
    IPYTHON_PAGING_PROMPT
  };


  public static final Key<PythonConsoleData> PYTHON_CONSOLE_DATA = Key.create("python-console-data");

  private PyConsoleUtil() {
  }

  public static boolean isPagingPrompt(@Nullable String prompt) {
    return prompt != null && IPYTHON_PAGING_PROMPT.equals(prompt.trim());
  }

  static String processPrompts(final LanguageConsoleView languageConsole, String string) {
    // Change prompt
    for (String prompt : PROMPTS) {
      if (string.startsWith(prompt)) {
        // Process multi prompts here
        if (prompt != HELP_PROMPT) {
          final StringBuilder builder = new StringBuilder();
          builder.append(prompt).append(prompt);
          while (string.startsWith(builder.toString())) {
            builder.append(prompt);
          }
          final String multiPrompt = builder.substring(prompt.length());
          if (prompt == INDENT_PROMPT) {
            prompt = multiPrompt;
          }
          string = string.substring(multiPrompt.length());
        }
        else {
          string = string.substring(prompt.length());
        }

        // Change console editor prompt if required
        final String currentPrompt = languageConsole.getPrompt();
        final String trimmedPrompt = prompt.trim();
        if (currentPrompt != null && !currentPrompt.equals(trimmedPrompt)) {
          languageConsole.setPrompt(trimmedPrompt);
          scrollDown(languageConsole.getConsoleEditor());
        }
        break;
      }
    }
    return string;
  }


  public static void scrollDown(final Editor currentEditor) {
    ApplicationManager.getApplication().invokeLater(
      () -> {
        if (!currentEditor.isDisposed()) currentEditor.getCaretModel().moveToOffset(currentEditor.getDocument().getTextLength());
      }
    );
  }


  public static boolean detectIPythonImported(@NotNull String text, final ConsoleViewContentType outputType) {
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

  @NotNull
  public static PythonConsoleData getOrCreateIPythonData(@NotNull VirtualFile file) {
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

  public static void setCurrentIndentSize(@NotNull VirtualFile file, int indentSize) {
    PythonConsoleData consoleData = getOrCreateIPythonData(file);
    consoleData.setIndentSize(indentSize);
  }

  public static AnAction createTabCompletionAction(PythonConsoleView consoleView) {
    final AnAction runCompletions = new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = consoleView.getConsoleEditor();
        if (LookupManager.getActiveLookup(editor) != null) {
          AnAction replace = ActionManager.getInstance().getAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE);
          ActionUtil.performActionDumbAwareWithCallbacks(replace, e);
          return;
        }
        AnAction completionAction = ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION);
        if (completionAction != null) {
          ActionUtil.performActionDumbAwareWithCallbacks(completionAction, e);
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
    };

    runCompletions.registerCustomShortcutSet(KeyEvent.VK_TAB, 0, consoleView.getConsoleEditor().getComponent());
    return runCompletions;
  }

  public static AnAction createInterruptAction(PythonConsoleView consoleView) {
    AnAction anAction = new AnAction() {
      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
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
      public void update(@NotNull final AnActionEvent e) {
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
    };

    anAction.registerCustomShortcutSet(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, consoleView.getConsoleEditor().getComponent());
    anAction.registerCustomShortcutSet(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, consoleView.getHistoryViewer().getComponent());
    return anAction;
  }

  public static AnAction createScrollToEndAction(@NotNull final Editor editor) {
    return new ScrollToTheEndToolbarAction(editor);
  }

  private static class ConsoleDataContext implements DataContext {
    private final DataContext myOriginalDataContext;
    private final PythonConsoleView myConsoleView;

    ConsoleDataContext(DataContext dataContext, PythonConsoleView consoleView) {
      myOriginalDataContext = dataContext;
      myConsoleView = consoleView;
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (CommonDataKeys.EDITOR.is(dataId)) {
        return myConsoleView.getEditor();
      }
      return myOriginalDataContext.getData(dataId);
    }
  }

  private static AnActionEvent createActionEvent(@NotNull AnActionEvent e, PythonConsoleView consoleView) {
    return e.withDataContext(new ConsoleDataContext(e.getDataContext(), consoleView));
  }

  public static AnAction createPrintAction(PythonConsoleView consoleView) {
    final AnAction printAction = ActionManager.getInstance().getAction("Print");
    return new DumbAwareAction() {
      {
        ActionUtil.copyFrom(this, "Print");
      }
      @Override
      public void update(@NotNull AnActionEvent e) {
        printAction.update(createActionEvent(e, consoleView));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        printAction.actionPerformed(createActionEvent(e, consoleView));
      }
    };
  }
}


