package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;

public abstract class IpnbRunCellBaseAction extends AnAction {
  public IpnbRunCellBaseAction() {
    super(AllIcons.General.Run);
  }

  public static void runCell(@NotNull final IpnbFilePanel ipnbFilePanel, boolean selectNext) {
    final IpnbEditablePanel cell = ipnbFilePanel.getSelectedCell();
    if (cell == null) return;
    cell.runCell();
    if (selectNext) {
      final int index = ipnbFilePanel.getSelectedIndex();
      if (ipnbFilePanel.getIpnbPanels().size()-1 == index) {
        ipnbFilePanel.createAndAddCell(true);
        ipnbFilePanel.saveToFile();
      }
      ipnbFilePanel.selectNext(cell);
    }
    ipnbFilePanel.revalidate();
    ipnbFilePanel.repaint();
    ipnbFilePanel.requestFocus();
  }
}
