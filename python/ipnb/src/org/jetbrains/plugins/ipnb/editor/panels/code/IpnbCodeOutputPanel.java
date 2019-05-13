// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.editor.panels.code;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbHideOutputAction;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbPanel;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class IpnbCodeOutputPanel<K extends IpnbOutputCell> extends IpnbPanel<JComponent, K> {
  protected final IpnbFilePanel myParent;
  private final IpnbCodePanel myCodePanel;

  public IpnbCodeOutputPanel(@NotNull final K cell, @Nullable final IpnbFilePanel parent, @Nullable final IpnbCodePanel codePanel) {
    super(cell, new BorderLayout());
    myParent = parent;
    myViewPanel = createViewPanel();
    add(myViewPanel);
    myCodePanel = codePanel;
    myViewPanel.addMouseListener(createHideOutputListener());
    addRightClickMenu();
  }

  @Override
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

  @Override
  public void addRightClickMenu() {
    myViewPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
          final ListPopup menu = createPopupMenu(new DefaultActionGroup(new IpnbHideOutputAction(myCodePanel)));
          menu.show(RelativePoint.fromScreen(e.getLocationOnScreen()));
        }
      }
    });
  }

  @NotNull
  private MouseAdapter createHideOutputListener() {
    return new MouseAdapter() {

      @Override
      public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
          myCodePanel.hideOutputPanel();
        }
      }
    };
  }
}
