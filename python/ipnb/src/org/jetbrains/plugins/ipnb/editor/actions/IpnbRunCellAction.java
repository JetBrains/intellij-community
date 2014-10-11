package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public class IpnbRunCellAction extends AnAction {
  public IpnbRunCellAction() {
    super(AllIcons.General.Run);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(context);
    if (editor instanceof IpnbFileEditor) {
      final IpnbFilePanel component = ((IpnbFileEditor)editor).getIpnbFilePanel();
      runCell(component, false);
    }
  }

  public void runCell(@NotNull final IpnbFilePanel ipnbFilePanel, boolean selectNext) {
    final IpnbEditablePanel cell = ipnbFilePanel.getSelectedCell();
    cell.runCell();
    if (selectNext) {
      final int index = ipnbFilePanel.getSelectedIndex();
      if (ipnbFilePanel.getIpnbPanels().size()-1 == index) {
        ipnbFilePanel.createAndAddCell();
      }
      ipnbFilePanel.selectNext(cell);
    }
    ipnbFilePanel.revalidate();
    ipnbFilePanel.repaint();
    ipnbFilePanel.requestFocus();
  }
}
