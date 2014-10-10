package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbHtmlOutputCell;

import javax.swing.*;

public class IpnbHtmlPanel extends IpnbCodeOutputPanel<IpnbHtmlOutputCell> {
  public IpnbHtmlPanel(@NotNull final IpnbHtmlOutputCell cell) {
    super(cell);
  }

  @Override
  protected JComponent createViewPanel() {
    final StringBuilder text = new StringBuilder("<html>");
    for (String html : myCell.getHtmls()) {
      html = html.replace("\"", "'");
      text.append(html);
    }
    text.append("</html>");

    final JBLabel label = new JBLabel(text.toString());
    label.setBackground(IpnbEditorUtil.getBackground());
    label.setOpaque(true);
    return label;
  }
}
