package com.jetbrains.python.edu;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PyStudyInstructionPainter extends EditorEmptyTextPainter {
  private static final String separator = " / ";

  @Override
  protected void advertiseActions(@NotNull JComponent splitters, @NotNull UIUtil.TextPainter painter) {
    String shortcut = KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke("ctrl pressed ENTER"), null));
    appendAction(painter, "Navigate to the next answer placeholder", shortcut);
    appendAction(painter, "Navigate between answer placeholders", getActionShortcutText(EduActionIds.PREV_WINDOW) + separator +
                                                                  getActionShortcutText(EduActionIds.NEXT_WINDOW));
    appendAction(painter, "Navigate between tasks", getActionShortcutText(EduActionIds.PREV_TASK) + separator +
                                                    getActionShortcutText(EduActionIds.NEXT_TASK));
    appendAction(painter, "Reset current task file", getActionShortcutText(EduActionIds.REFRESH));
    appendAction(painter, "Check task", getActionShortcutText(EduActionIds.CHECK));
    appendAction(painter, "Get hint for the answer placeholder", getActionShortcutText(EduActionIds.HINT));
  }
}