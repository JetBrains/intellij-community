package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.HeadingCell;

public class HeadingPanel extends IpnbEditablePanel<JBLabel> {
  private final HeadingCell myCell;

  public HeadingPanel(@NotNull final HeadingCell cell) {
    super();
    myCell = cell;
    initPanel();
  }

  @Override
  protected String getRawCellText() {
    return myCell.getSourceAsString();
  }

  private String renderCellText() {
    return "<html><h" + myCell.getLevel() + ">" + myCell.getSourceAsString() + "</h" + myCell.getLevel() + "></html>";
  }

  @Override
  public void updateCellView() {
    final String text = myEditablePanel.getText();
    myCell.setSource(StringUtil.splitByLines(text));
    myViewPanel.setText(renderCellText());
  }

  public HeadingCell getCell() {
    return myCell;
  }

  @Override
  protected JBLabel createViewPanel() {
    final JBLabel label = new JBLabel(renderCellText());
    label.setBackground(IpnbEditorUtil.getBackground());
    label.setOpaque(true);

    return label;
  }

}
