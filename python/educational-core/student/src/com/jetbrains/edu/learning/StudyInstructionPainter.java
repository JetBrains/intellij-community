package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.ui.StudyProgressToolWindowFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StudyInstructionPainter extends EditorEmptyTextPainter {
  private static final String separator = " / ";
  @Override
  protected void advertiseActions(@NotNull JComponent splitters, @NotNull UIUtil.TextPainter painter) {
    if (PlatformUtils.isPyCharmEducational()) {
      String shortcut = KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(StudyNextWindowAction.SHORTCUT2), null));
      appendAction(painter, "Navigate to the next answer placeholder", shortcut);
      appendAction(painter, "Navigate between answer placeholders", getActionShortcutText(StudyPrevWindowAction.ACTION_ID) + separator +
                                                                    getActionShortcutText(StudyNextWindowAction.ACTION_ID));
      appendAction(painter, "Navigate between tasks", getActionShortcutText(StudyPreviousStudyTaskAction.ACTION_ID) + separator +
                                                      getActionShortcutText(StudyNextStudyTaskAction.ACTION_ID));
      appendAction(painter, "Reset current task file", getActionShortcutText(StudyRefreshTaskFileAction.ACTION_ID));
      appendAction(painter, "Check task", getActionShortcutText(StudyCheckAction.ACTION_ID));
      appendAction(painter, "Get hint for the answer placeholder", getActionShortcutText(StudyShowHintAction.ACTION_ID));
      appendLine(painter, "To see your progress open the '" + StudyProgressToolWindowFactory.ID + "' panel");
    } else {
      super.advertiseActions(splitters, painter);
    }
  }
}