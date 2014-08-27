package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.IpnbHeadingCell;

public class IpnbHeadingPanel extends IpnbEditablePanel<JBLabel, IpnbHeadingCell> {

  public IpnbHeadingPanel(@NotNull final IpnbHeadingCell cell) {
    super(cell);
    myCell = cell;
    initPanel();
  }

  @Override
  protected String getRawCellText() {
    return myCell.getSourceAsString();
  }

  private String renderCellText() {
    return "<html><body style='width: " + IpnbEditorUtil.PANEL_WIDTH + "px'><h" + myCell.getLevel() + ">" + myCell.getSourceAsString() + "</h" + myCell.getLevel() +
           "></body></html>";
  }

  @Override
  public void updateCellView() {
    final String text = myEditablePanel.getText();
    myCell.setSource(StringUtil.splitByLines(text));
    myViewPanel.setText(renderCellText());
  }

  @Override
  protected JBLabel createViewPanel() {
    final JBLabel label = new JBLabel(renderCellText());
    label.setBackground(IpnbEditorUtil.getBackground());
    label.setOpaque(true);

    return label;
  }

}
