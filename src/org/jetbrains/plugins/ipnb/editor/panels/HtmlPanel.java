package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.output.HtmlCellOutput;

import javax.swing.*;
import java.awt.*;

public class HtmlPanel extends IpnbPanel {
  @NotNull private final Project myProject;

  public HtmlPanel(@NotNull final Project project, @NotNull final HtmlCellOutput cell) {
    myProject = project;
    add(createPanel(cell), BorderLayout.CENTER);
  }

  private JLabel createPanel(@NotNull final HtmlCellOutput cell) {
    String[] htmls = cell.getHtmls();
    final StringBuilder text = new StringBuilder("<html>");
    for (String html : htmls) {
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
