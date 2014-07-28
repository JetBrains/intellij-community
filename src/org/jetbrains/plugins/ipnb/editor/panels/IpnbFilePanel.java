/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.ipnb.editor.panels;

import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.cells.CodeCell;
import org.jetbrains.plugins.ipnb.format.cells.HeadingCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;
import org.jetbrains.plugins.ipnb.format.cells.MarkdownCell;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author traff
 */
public class IpnbFilePanel extends JPanel {

  public static final int INSET_Y = 10;
  public static final int INSET_X = 5;
  private final IpnbFile myIpnbFile;

  private Project myProject;
  @Nullable private Disposable myParent;

  private final List<IpnbPanel> myIpnbPanels = Lists.newArrayList();

  private IpnbPanel mySelectedCell;

  public IpnbFilePanel(@NotNull final Project project, @Nullable final Disposable parent, @NotNull final IpnbFile file) {
    super(new GridBagLayout());
    myProject = project;
    myParent = parent;
    setBackground(IpnbEditorUtil.getBackground());
    myIpnbFile = file;

    layoutFile(file);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        updateCellSelection(e);
      }
    });

    setFocusable(true);
  }

  private void layoutFile(IpnbFile file) {
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 1;
    c.insets = new Insets(INSET_Y, INSET_X, 0, 0);

    final JPanel panel = new JPanel();
    panel.setPreferredSize(IpnbEditorUtil.PROMPT_SIZE);
    panel.setBackground(getBackground());
    add(panel);

    for (IpnbCell cell : file.getCells()) {
      c.gridy = addCellToPanel(cell, c);
    }

    c.weighty = 1;
    add(createEmptyPanel(), c);
  }

  private int addCellToPanel(IpnbCell cell, GridBagConstraints c) {
    if (cell instanceof CodeCell) {
      final CodePanel comp = new CodePanel(myProject, myParent, (CodeCell)cell);
      c.gridwidth = 2;
      c.gridx = 0;
      add(comp, c);
      myIpnbPanels.add(comp);
    }
    else if (cell instanceof MarkdownCell) {
      final MarkdownPanel comp = new MarkdownPanel(myProject, (MarkdownCell)cell);
      addComponent(c, comp);
    }
    else if (cell instanceof HeadingCell) {
      final HeadingPanel comp = new HeadingPanel((HeadingCell)cell);
      addComponent(c, comp);
    }
    else {
      throw new UnsupportedOperationException(cell.getClass().toString());
    }
    return c.gridy + 1;
  }

  private void addComponent(@NotNull final GridBagConstraints c, @NotNull final IpnbPanel comp) {
    c.gridwidth = 1;
    c.gridx = 1;
    add(comp, c);

    myIpnbPanels.add(comp);
  }

  private JPanel createEmptyPanel() {
    JPanel panel = new JPanel();
    panel.setBackground(IpnbEditorUtil.getBackground());
    return panel;
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    if (mySelectedCell != null && e.getID() == KeyEvent.KEY_PRESSED) {
      if (e.getKeyCode() == KeyEvent.VK_UP) {
        selectPrev(mySelectedCell);
      }

      else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
        selectNext(mySelectedCell);
      }
    }
  }

  private void selectPrev(@NotNull IpnbPanel cell) {
    int index = myIpnbPanels.indexOf(cell);
    if (index > 0) {
      setSelectedCell(myIpnbPanels.get(index - 1));
    }
  }

  private void selectNext(@NotNull IpnbPanel cell) {
    int index = myIpnbPanels.indexOf(cell);
    if (index < myIpnbPanels.size() - 1) {
      setSelectedCell(myIpnbPanels.get(index + 1));
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (mySelectedCell != null) {
      g.setColor(mySelectedCell.isEditing() ? JBColor.GREEN : JBColor.GRAY);
      g.drawRect(100, mySelectedCell.getTop() - 1, getWidth() - 200, mySelectedCell.getHeight() + 2);
    }
  }

  private void updateCellSelection(MouseEvent e) {
    if (e.getClickCount() > 0) {
      IpnbPanel ipnbPanel = getIpnbPanelByClick(e.getPoint());
      if (ipnbPanel != null) {
        setSelectedCell(ipnbPanel);
      }
    }
  }

  public void setSelectedCell(IpnbPanel IpnbPanel) {
    mySelectedCell = IpnbPanel;
    requestFocus();
    repaint();
  }

  public IpnbPanel getSelectedCell() {
    return mySelectedCell;
  }

  @Nullable
  private IpnbPanel getIpnbPanelByClick(Point point) {
    for (IpnbPanel c: myIpnbPanels) {
      if (c.contains(point.y)) {
        return c;
      }
    }
    return null;
  }
}
