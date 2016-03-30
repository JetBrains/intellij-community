package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.IpnbHeadingCell;

public class IpnbHeadingPanel extends IpnbEditablePanel<JBLabel, IpnbHeadingCell> {

  public IpnbHeadingPanel(@NotNull final IpnbHeadingCell cell) {
    super(cell);
    initPanel();
  }

  @Override
  protected String getRawCellText() {
    return myCell.getSourceAsString();
  }

  private String renderCellText() {
    return "<html><body><h" + myCell.getLevel() + " style=\"font-size: " + EditorColorsManager
      .getInstance().getGlobalScheme().getEditorFontSize() + ";\">" + myCell.getSourceAsString() + "</h" + myCell.getLevel() +
           "></body></html>";
  }

  @Override
  public void updateCellView() {
    myViewPanel.setText(renderCellText());
  }

  @Override
  protected JBLabel createViewPanel() {
    final JBLabel label = new JBLabel(renderCellText());
    label.setBackground(IpnbEditorUtil.getBackground());
    label.setOpaque(true);

    return label;
  }

  @SuppressWarnings("CloneDoesntCallSuperClone")
  @Override
  protected Object clone() {
    return new IpnbHeadingPanel((IpnbHeadingCell)myCell.clone());
  }
}
