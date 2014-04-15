package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.output.CellOutput;

import javax.swing.*;
import java.awt.*;

public class CodeOutputPanel extends JPanel {
  private final Project myProject;

  public CodeOutputPanel(@NotNull final Project project, @NotNull final CellOutput cell, int promptNumber) {
    myProject = project;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    final String[] text = cell.getText();
    if (text != null)
      add(createContainer(outputPrompt(promptNumber), new JTextArea(StringUtil.join(text, "\n"))));
  }

  private JPanel createContainer(@NotNull String promptText, @NotNull final JComponent component) {
    JPanel container = new JPanel(new BorderLayout());
    JPanel p = new JPanel(new BorderLayout());
    p.add(new JLabel(promptText), BorderLayout.WEST);
    add(p, BorderLayout.NORTH);

    container.add(component, BorderLayout.CENTER);

    return container;
  }

  private String outputPrompt(int promptNumber) {
    return String.format("Out[%d]:", promptNumber);
  }

}
