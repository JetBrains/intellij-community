package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

public class RubyMineEmptyTextPainter extends EditorEmptyTextPainter {
  protected void advertiseActions(@NotNull JComponent splitters, @NotNull UIUtil.TextPainter painter) {
    appendSearchEverywhere(painter);
    appendRunAnything(painter);
    appendToolWindow(painter, "Project View", ToolWindowId.PROJECT_VIEW, splitters);
    appendAction(painter, "Go to File", getActionShortcutText("GotoFile"));
    appendAction(painter, "Recent Files", getActionShortcutText(IdeActions.ACTION_RECENT_FILES));
    appendAction(painter, "Navigation Bar", getActionShortcutText("ShowNavBar"));
    appendDnd(painter);
  }

  protected void appendRunAnything(@NotNull UIUtil.TextPainter painter) {
    Shortcut[] shortcuts = getActiveKeymapShortcuts("RunAnything").getShortcuts();
    appendAction(painter, "Run Anything", shortcuts.length == 0 ?
                                               "Double " + (SystemInfo.isMac ? MacKeymapUtil.CONTROL : "Ctrl") :
                                               KeymapUtil.getShortcutsText(shortcuts));
  }
}