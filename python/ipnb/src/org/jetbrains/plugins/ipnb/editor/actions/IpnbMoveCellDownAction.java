package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbMoveCellDownAction extends AnAction {
  private final IpnbFileEditor myEditor;

  public IpnbMoveCellDownAction(IpnbFileEditor editor) {
    super("Move cell down", "Move cell down", AllIcons.Actions.MoveDown);
    myEditor = editor;
    registerCustomShortcutSet(new CustomShortcutSet(KeyboardShortcut.fromString(SystemInfo.isMac ? "meta shift DOWN" : "control shift DOWN")),
                              myEditor.getIpnbFilePanel());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel ipnbFilePanel = myEditor.getIpnbFilePanel();
    ipnbFilePanel.executeSaveFileCommand();
    ipnbFilePanel.executeUndoableCommand(() -> ipnbFilePanel.moveCell(true), "Move Cell");
  }

  @Override
  public void update(AnActionEvent e) {
    IpnbFilePanel ipnbFilePanel = myEditor.getIpnbFilePanel();
    IpnbEditablePanel selectedCellPanel = ipnbFilePanel.getSelectedCellPanel();
    if (selectedCellPanel != null) {
      boolean editing = selectedCellPanel.isEditing();
      e.getPresentation().setEnabled(!editing);
    }
  }
}
