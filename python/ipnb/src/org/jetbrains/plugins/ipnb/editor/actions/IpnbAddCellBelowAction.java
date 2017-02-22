package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;

public class IpnbAddCellBelowAction extends AnAction {

  private final IpnbFileEditor myFileEditor;

  public IpnbAddCellBelowAction(IpnbFileEditor fileEditor) {
    super("Insert Cell Below", "Insert Cell Below", AllIcons.General.Add);
    myFileEditor = fileEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel component = myFileEditor.getIpnbFilePanel();
    addCell(component);
  }

  public static void addCell(@NotNull final IpnbFilePanel ipnbFilePanel) {
    CommandProcessor.getInstance().executeCommand(ipnbFilePanel.getProject(), () -> ApplicationManager.getApplication().runWriteAction(
      () -> {
        ipnbFilePanel.createAndAddCell(true, IpnbCodeCell.createEmptyCodeCell());
        ipnbFilePanel.saveToFile(false);
      }), "Ipnb.createAndAddCell", new Object());
  }
}
