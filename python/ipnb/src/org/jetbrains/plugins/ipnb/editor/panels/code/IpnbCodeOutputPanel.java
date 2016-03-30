package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbPanel;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;

import javax.swing.*;
import java.awt.*;

public class IpnbCodeOutputPanel<K extends IpnbOutputCell> extends IpnbPanel<JComponent, K> {
  protected final IpnbFilePanel myParent;

  public IpnbCodeOutputPanel(@NotNull final K cell, @Nullable final IpnbFilePanel parent) {
    super(cell, new BorderLayout());
    myParent = parent;
    myViewPanel = createViewPanel();
    add(myViewPanel);
  }

  protected JComponent createViewPanel() {
    JTextArea textArea = new JTextArea(myCell.getSourceAsString());
    textArea.setLineWrap(true);
    textArea.setEditable(false);
    final Font font = textArea.getFont();
    final Font newFont = new Font(font.getName(), font.getStyle(), EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize());
    textArea.setFont(newFont);
    textArea.setBackground(IpnbEditorUtil.getBackground());
    return textArea;
  }
}
