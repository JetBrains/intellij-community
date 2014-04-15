package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class CodeOutputPanel extends JPanel {
  public CodeOutputPanel(@NotNull String outputText) {
    super(new BorderLayout());

    JTextArea textArea = new JTextArea(outputText);
    textArea.setEditable(false);
    add(textArea, BorderLayout.CENTER);
  }
}
