package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.HeadingCell;

import javax.swing.*;
import java.awt.*;

public class HeadingPanel extends JPanel {
  @NotNull private final Project myProject;

  public HeadingPanel(@NotNull final Project project, @NotNull final HeadingCell cell) {
    super(new BorderLayout());
    myProject = project;
    add(createPanel(cell), BorderLayout.CENTER);
  }

  private JLabel createPanel(@NotNull final HeadingCell cell) {
    final String text = "<html><h" + cell.getLevel() + ">" + cell.getSourceAsString() + "</h" + cell.getLevel() + "></html>";
    JBLabel label = new JBLabel(text);
    label.setBackground(IpnbEditorUtil.getBackground());
    label.setOpaque(true);
    return label;
  }

}
