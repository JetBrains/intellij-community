package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class IpnbAddCellBelowAction extends AnAction {

  private final IpnbFileEditor myFileEditor;

  public IpnbAddCellBelowAction(IpnbFileEditor fileEditor) {
    super("Insert Cell Below", "Insert Cell Below", AllIcons.General.Add);
    myFileEditor = fileEditor;
    KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK);
    registerCustomShortcutSet(new CustomShortcutSet(keyStroke), myFileEditor.getIpnbFilePanel());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    addCell(myFileEditor.getIpnbFilePanel());
  }

  public static void addCell(@NotNull final IpnbFilePanel ipnbFilePanel) {
    ipnbFilePanel.executeUndoableCommand(() -> {
      ipnbFilePanel.createAndAddCell(true, IpnbCodeCell.createEmptyCodeCell());
      ipnbFilePanel.saveToFile(false);
    }, "Create cell");
  }

  @Override
  public void update(AnActionEvent e) {
    IpnbEditablePanel panel = myFileEditor.getIpnbFilePanel().getSelectedCellPanel();
    if (panel != null && panel.isEditing()) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(true);
    }
  }
}
