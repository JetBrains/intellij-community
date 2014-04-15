package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.cells.output.CellOutput;

import javax.swing.*;
import java.awt.*;

public class CodeOutputPanel extends JPanel {
  private final Project myProject;

  public CodeOutputPanel(@NotNull final Project project, @NotNull final CellOutput cell, int promptNumber) {
    myProject = project;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    final String text = cell.getSourceAsString();
    if (text != null) {
      add(IpnbEditorUtil.createPanelWithPrompt(outputPrompt(promptNumber), new JTextArea(text)));
    }
  }

  private String outputPrompt(int promptNumber) {
    return String.format("Out[%d]:", promptNumber);
  }
}
