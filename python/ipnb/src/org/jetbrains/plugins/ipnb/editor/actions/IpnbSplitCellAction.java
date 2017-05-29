package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbSplitCellAction extends AnAction {

  public IpnbSplitCellAction() {
    super("Split Cell");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final IpnbFileEditor ipnbEditor = IpnbFileEditor.DATA_KEY.getData(context);
    if (ipnbEditor != null) {
      final IpnbFilePanel ipnbFilePanel = ipnbEditor.getIpnbFilePanel();
      splitCell(ipnbFilePanel);
    }
  }

  public static void splitCell(IpnbFilePanel filePanel) {
    filePanel.executeSaveFileCommand();
    filePanel.executeUndoableCommand(() -> {
      filePanel.splitCell();
      filePanel.saveToFile(false);
    }, "Split Cell");
  }
}
