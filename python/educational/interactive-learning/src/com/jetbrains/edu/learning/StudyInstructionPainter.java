package com.jetbrains.edu.learning;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Couple;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.PairFunction;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.actions.*;

import javax.swing.*;
import java.awt.*;

/**
 * author: liana
 * data: 7/29/14.
 */
public class StudyInstructionPainter extends EditorEmptyTextPainter {
  @Override
  public void paintEmptyText(final JComponent splitters, Graphics g) {
    boolean isDarkBackground = UIUtil.isUnderDarcula();
    UISettings.setupAntialiasing(g);

    g.setColor(new JBColor(isDarkBackground ? Gray._230 : Gray._80, Gray._160));
    g.setFont(UIUtil.getLabelFont().deriveFont(isDarkBackground ? 24f : 20f));

    UIUtil.TextPainter painter = new UIUtil.TextPainter().withLineSpacing(1.5f);

    painter.appendLine("Educational Edition").underlined(new JBColor(Gray._150, Gray._180));
    addAction(painter, "Navigate to the next answer placeholder", StudyNextWindowAction.ACTION_ID, StudyNextWindowAction.SHORTCUT2, true);
    String shortcut1 = getShortcutText(StudyPrevWindowAction.ACTION_ID, StudyPrevWindowAction.SHORTCUT, false, false);
    String shortcut2 = getShortcutText(StudyNextWindowAction.ACTION_ID, StudyNextWindowAction.SHORTCUT, false, false);
    String text = "Navigate between answer placeholders with " + shortcut1 +
                  " and " + shortcut2;
    painter.appendLine(text).smaller().withBullet();
    shortcut1 = getShortcutText(StudyPreviousStudyTaskAction.ACTION_ID, StudyPreviousStudyTaskAction.SHORTCUT, false, false);
    shortcut2 = getShortcutText(StudyNextStudyTaskAction.ACTION_ID, StudyNextStudyTaskAction.SHORTCUT, false, false);
    painter.appendLine("Navigate between tasks with " + shortcut1 + " and " + shortcut2).smaller().withBullet();
    addAction(painter, "Reset current task file", StudyRefreshTaskFileAction.ACTION_ID, StudyRefreshTaskFileAction.SHORTCUT, false);
    addAction(painter, "Check task", StudyCheckAction.ACTION_ID, StudyCheckAction.SHORTCUT, false);
    addAction(painter, "Get hint for the answer placeholder", StudyShowHintAction.ACTION_ID, StudyShowHintAction.SHORTCUT, false);
    painter.appendLine("To see your progress open the 'Course Description' panel").smaller().withBullet();
                       painter.draw(g, new PairFunction<Integer, Integer, Couple<Integer>>() {
                         @Override
                         public Couple<Integer> fun(Integer width, Integer height) {
                           Dimension s = splitters.getSize();
                           return Couple.of((s.width - width) / 2, (s.height - height) / 2);
                         }
                       });
  }
  private static void addAction(UIUtil.TextPainter painter, String text, String actionId, String defaultShortcutString, boolean useDefault) {
    String shortcut = getShortcutText(actionId, defaultShortcutString, useDefault, true);
    String actionText = text + " with " + shortcut;
    painter.appendLine(actionText).smaller().withBullet();
  }

  private static String getShortcutText(String actionId, String defaultShortcutString, boolean useDefault, boolean wrapTag) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    String shortcut = "";
    if (!useDefault) {
      shortcut = KeymapUtil.getFirstKeyboardShortcutText(action);
    }
    if (shortcut.isEmpty() && defaultShortcutString != null) {
      KeyboardShortcut keyboardShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(defaultShortcutString), null);
      shortcut = KeymapUtil.getShortcutText(keyboardShortcut);
    }
    if (!wrapTag) {
      return shortcut;
    }
    return "<shortcut>" + shortcut + "</shortcut>";
  }
}
