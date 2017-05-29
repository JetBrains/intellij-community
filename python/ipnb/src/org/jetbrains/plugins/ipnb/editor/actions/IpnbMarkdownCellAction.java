package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbEditableCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;

import java.util.List;

public class IpnbMarkdownCellAction extends AnAction {
  private final IpnbFileEditor myEditor;

  public IpnbMarkdownCellAction(IpnbFileEditor editor) {
    super("Markdown Cell");
    myEditor = editor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    changeTypeToMarkdown(myEditor);
  }

  public void changeTypeToMarkdown(@NotNull final IpnbFileEditor editor) {
    final IpnbFilePanel filePanel = editor.getIpnbFilePanel();
    final IpnbEditablePanel selectedCellPanel = filePanel.getSelectedCellPanel();
    if (selectedCellPanel == null) return;
    final IpnbEditableCell cell = selectedCellPanel.getCell();

    final List<IpnbCell> cells = filePanel.getIpnbFile().getCells();
    final int index = cells.indexOf(selectedCellPanel.getCell());
    final IpnbMarkdownCell markdownCell = new IpnbMarkdownCell(cell.getSource(), cell.getMetadata());
    filePanel.executeSaveFileCommand();
    filePanel.executeUndoableCommand(
      () -> {
        if (index >= 0) {
          cells.set(index, markdownCell);
        }
        filePanel.replaceComponent(selectedCellPanel, markdownCell);
        filePanel.saveToFile(false);
      },
      "Change Cell Type To Markdown");
  }
}
