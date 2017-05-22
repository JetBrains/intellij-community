package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;

public class IpnbAddCellAboveAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final FileEditor editor = IpnbFileEditor.DATA_KEY.getData(context);
    if (editor != null) {
      final IpnbFilePanel component = ((IpnbFileEditor)editor).getIpnbFilePanel();
      addCell(component);
    }
  }

  public void addCell(@NotNull final IpnbFilePanel ipnbFilePanel) {
    CommandProcessor.getInstance().executeCommand(ipnbFilePanel.getProject(), () -> ApplicationManager.getApplication().runWriteAction(
      () -> {
        ipnbFilePanel.createAndAddCell(false, IpnbCodeCell.createEmptyCodeCell());
        ipnbFilePanel.saveToFile(false);
      }), "Ipnb.createAndAddCell", new Object());

  }
}
