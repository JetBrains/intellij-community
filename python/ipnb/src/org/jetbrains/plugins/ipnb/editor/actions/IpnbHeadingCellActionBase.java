package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbEditableCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbHeadingCell;

import java.util.List;

public class IpnbHeadingCellActionBase extends AnAction {
  private final int myLevel;

  public IpnbHeadingCellActionBase(int level) {
    myLevel = level;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(context);
    if (editor instanceof IpnbFileEditor) {
      changeTypeToHeading((IpnbFileEditor)editor);
    }
  }

  private void changeTypeToHeading(@NotNull final IpnbFileEditor editor) {
    final IpnbFilePanel filePanel = editor.getIpnbFilePanel();
    final IpnbEditablePanel selectedCellPanel = filePanel.getSelectedCellPanel();
    if (selectedCellPanel == null) return;
    final IpnbEditableCell cell = selectedCellPanel.getCell();

    final List<IpnbCell> cells = filePanel.getIpnbFile().getCells();
    final int index = cells.indexOf(selectedCellPanel.getCell());
    final IpnbHeadingCell heading = new IpnbHeadingCell(cell.getSource(), myLevel, cell.getMetadata());
    filePanel.executeUndoableCommand(() -> {
      if (index >= 0) {
        cells.set(index, heading);
      }
      filePanel.replaceComponent(selectedCellPanel, heading);
    }, "Change Cell Type To Heading " + myLevel);
  }
}
