package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

import javax.swing.*;

public class IpnbDeleteCellAction extends AnAction {

  private final IpnbFileEditor myFileEditor;

  public IpnbDeleteCellAction(IpnbFileEditor fileEditor) {
    super(AllIcons.Actions.Delete);
    myFileEditor = fileEditor;
    registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("DELETE")), myFileEditor.getIpnbFilePanel());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel component = myFileEditor.getIpnbFilePanel();
    deleteCell(component);
  }

  public static void deleteCell(@NotNull final IpnbFilePanel ipnbFilePanel) {
    CommandProcessor.getInstance().executeCommand(ipnbFilePanel.getProject(), () -> ApplicationManager.getApplication().runWriteAction(
      () -> {
        ipnbFilePanel.deleteSelectedCell();
        ipnbFilePanel.saveToFile(false);
      }), "Ipnb.deleteCell", new Object());
  }
}
