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
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;

import java.util.List;

public class IpnbMarkdownCellAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(context);
    if (editor instanceof IpnbFileEditor) {
      changeTypeToMarkdown((IpnbFileEditor)editor);
    }
  }

  public void changeTypeToMarkdown(@NotNull final IpnbFileEditor editor) {
    final IpnbFilePanel filePanel = editor.getIpnbFilePanel();
    final IpnbEditablePanel selectedCell = filePanel.getSelectedCell();
    if (selectedCell == null) return;
    final IpnbEditableCell cell = selectedCell.getCell();

    final List<IpnbCell> cells = filePanel.getIpnbFile().getCells();
    final int index = cells.indexOf(selectedCell.getCell());
    final IpnbMarkdownCell markdownCell = new IpnbMarkdownCell(cell.getSource(), cell.getMetadata());
    if (index >= 0)
      cells.set(index, markdownCell);
    filePanel.replaceComponent(selectedCell, markdownCell);
  }
}
