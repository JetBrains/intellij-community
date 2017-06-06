package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;

public class IpnbAddCellAboveAction extends AnAction {

  private final IpnbFileEditor myFileEditor;

  public IpnbAddCellAboveAction(IpnbFileEditor fileEditor) {
    super("Insert Cell Above");
    myFileEditor = fileEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final IpnbFilePanel component = myFileEditor.getIpnbFilePanel();
    addCell(component);
  }

  public void addCell(@NotNull final IpnbFilePanel ipnbFilePanel) {
    ipnbFilePanel.executeUndoableCommand(() -> {
      ipnbFilePanel.createAndAddCell(false, IpnbCodeCell.createEmptyCodeCell());
      ipnbFilePanel.saveToFile(false);
    }, "Add cell above");
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final IpnbFileEditor editor = IpnbFileEditor.DATA_KEY.getData(context);
    if (editor == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    IpnbFilePanel panel = editor.getIpnbFilePanel();
    IpnbEditablePanel selectedCellPanel = panel.getSelectedCellPanel();
    if (selectedCellPanel != null && selectedCellPanel.isEditing()) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setEnabledAndVisible(true);
    }
  }
}
