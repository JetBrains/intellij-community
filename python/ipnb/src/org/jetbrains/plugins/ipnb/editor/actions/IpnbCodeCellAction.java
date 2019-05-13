package org.jetbrains.plugins.ipnb.editor.actions;

import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbEditableCell;

import java.util.List;

public class IpnbCodeCellAction extends AnAction {
  private final IpnbFileEditor myEditor;

  public IpnbCodeCellAction(IpnbFileEditor editor) {
    super("Code Cell");
    myEditor = editor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    changeTypeToCode(myEditor);
  }

  private static void changeTypeToCode(@NotNull final IpnbFileEditor editor) {
    final IpnbFilePanel filePanel = editor.getIpnbFilePanel();
    final IpnbEditablePanel selectedCellPanel = filePanel.getSelectedCellPanel();
    if (selectedCellPanel == null) return;
    final IpnbEditableCell cell = selectedCellPanel.getCell();

    final List<IpnbCell> cells = filePanel.getIpnbFile().getCells();
    final int index = cells.indexOf(selectedCellPanel.getCell());
    final IpnbCodeCell codeCell = new IpnbCodeCell("python", cell.getSource(), null, Lists.newArrayList(), cell.getMetadata());
    filePanel.executeUndoableCommand(() -> {
      if (index >= 0) {
        cells.set(index, codeCell);
      }
      filePanel.replaceComponent(selectedCellPanel, codeCell);
      filePanel.saveToFile(false);
    }, "Change Cell Type To Code");
  }
}
