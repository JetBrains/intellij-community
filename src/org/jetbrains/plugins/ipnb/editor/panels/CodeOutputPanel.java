package org.jetbrains.plugins.ipnb.editor.panels;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class CodeOutputPanel extends IpnbPanel {
  public CodeOutputPanel(@NotNull String outputText) {

    JTextArea textArea = new JTextArea(outputText);
    textArea.setEditable(false);
    add(textArea, BorderLayout.CENTER);
  }
}
