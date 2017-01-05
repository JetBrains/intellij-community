package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
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
    CommandProcessor.getInstance().executeCommand(filePanel.getProject(), () -> ApplicationManager.getApplication().runWriteAction(
      () -> filePanel.splitCell()), "Ipnb.splitCell", new Object());
  }
}
