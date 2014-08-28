package org.jetbrains.plugins.ipnb.editor.panels;

import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.cells.*;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class IpnbFilePanel extends JPanel {

  public static final int INSET_Y = 10;
  public static final int INSET_X = 5;
  private final IpnbFile myIpnbFile;

  private Project myProject;
  @Nullable private Disposable myParent;
  @NotNull private final IpnbFileEditor.CellSelectionListener myListener;

  private final List<IpnbEditablePanel> myIpnbPanels = Lists.newArrayList();

  private IpnbEditablePanel mySelectedCell;
  private IpnbEditablePanel myBufferPanel;

  public IpnbFilePanel(@NotNull final Project project, @Nullable final Disposable parent, @NotNull final IpnbFile file,
                       @NotNull final IpnbFileEditor.CellSelectionListener listener) {
    super(new GridBagLayout());
    myProject = project;
    myParent = parent;
    myListener = listener;
    setBackground(IpnbEditorUtil.getBackground());
    myIpnbFile = file;

    layoutFile();

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        updateCellSelection(e);
      }
    });

    setFocusable(true);
    UIUtil.requestFocus(this);
  }

  private void layoutFile() {
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 1;
    c.insets = new Insets(INSET_Y, INSET_X, 0, 0);

    final JPanel panel = new JPanel();
    panel.setPreferredSize(IpnbEditorUtil.PROMPT_SIZE);
    panel.setBackground(getBackground());
    panel.setOpaque(false);
    add(panel);

    for (IpnbCell cell : myIpnbFile.getCells()) {
      c.gridy = addCellToPanel(cell, c);
    }

    c.weighty = 1;
    add(createEmptyPanel(), c);
  }

  private int addCellToPanel(IpnbCell cell, GridBagConstraints c) {
    IpnbEditablePanel panel;
    if (cell instanceof IpnbCodeCell) {
      panel = new IpnbCodePanel(myProject, myParent, (IpnbCodeCell)cell);
      c.gridwidth = 2;
      c.gridx = 0;
      add(panel, c);
      myIpnbPanels.add(panel);
    }
    else if (cell instanceof IpnbMarkdownCell) {
      panel = new IpnbMarkdownPanel((IpnbMarkdownCell)cell);
      addComponent(c, panel);
    }
    else if (cell instanceof IpnbHeadingCell) {
      panel = new IpnbHeadingPanel((IpnbHeadingCell)cell);
      addComponent(c, panel);
    }
    else {
      throw new UnsupportedOperationException(cell.getClass().toString());
    }
    if (c.gridy == 0) {
      setSelectedCell(panel);
    }
    return c.gridy + 1;
  }

  public void createAndAddCell() {
    removeAll();
    final IpnbCodeCell cell = new IpnbCodeCell("python", new String[]{""}, null, new ArrayList<IpnbOutputCell>());
    final IpnbCodePanel codePanel = new IpnbCodePanel(myProject, myParent, cell);

    addCell(cell, codePanel);
  }

  private void addCell(IpnbEditableCell cell, IpnbEditablePanel panel) {
    final IpnbEditablePanel selectedCell = getSelectedCell();
    final int index = myIpnbPanels.indexOf(selectedCell);
    myIpnbFile.addCell(cell, index+1);
    myIpnbPanels.add(index + 1, panel);

    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 1;
    c.insets = new Insets(INSET_Y, INSET_X, 0, 0);

    final JPanel promptPanel = new JPanel();
    promptPanel.setPreferredSize(new Dimension(IpnbEditorUtil.PROMPT_SIZE.width, 1));
    promptPanel.setBackground(getBackground());
    promptPanel.setOpaque(false);
    add(promptPanel);

    c.gridy += 1;
    for (IpnbPanel comp : myIpnbPanels) {
      c.gridwidth = 1;
      c.gridx = 1;
      if (comp instanceof IpnbCodePanel) {
        c.gridwidth = 2;
        c.gridx = 0;
        add(comp, c);
      }
      else {
        add(comp, c);
      }
      c.gridy += 1;
    }
    c.weighty = 1;
    add(createEmptyPanel(), c);

    setSelectedCell(panel);
    revalidate();
    repaint();
  }

  public void cutCell() {
    myBufferPanel = getSelectedCell();
    selectNextOrPrev(myBufferPanel);
    final int index = myIpnbPanels.indexOf(myBufferPanel);
    if (index < 0) return;
    myIpnbPanels.remove(index);
    myIpnbFile.removeCell(index);

    remove(myBufferPanel);
  }

  public void pasteCell() {
    if (myBufferPanel == null) return;
    removeAll();
    final IpnbEditablePanel editablePanel = (IpnbEditablePanel)myBufferPanel.clone();
    addCell(editablePanel.getCell(), editablePanel);
  }

  public void replaceComponent(@NotNull final IpnbEditablePanel from, @NotNull final IpnbCell cell) {
    final GridBagConstraints c = ((GridBagLayout)getLayout()).getConstraints(from);
    final int index = myIpnbPanels.indexOf(from);
    IpnbEditablePanel panel;
    if (cell instanceof IpnbCodeCell) {
      panel = new IpnbCodePanel(myProject, myParent, (IpnbCodeCell)cell);
      c.gridwidth = 2;
      c.gridx = 0;
      add(panel, c);
    }
    else if (cell instanceof IpnbMarkdownCell) {
      panel = new IpnbMarkdownPanel((IpnbMarkdownCell)cell);
      c.gridwidth = 1;
      c.gridx = 1;
      add(panel, c);
    }
    else if (cell instanceof IpnbHeadingCell) {
      panel = new IpnbHeadingPanel((IpnbHeadingCell)cell);
      c.gridwidth = 1;
      c.gridx = 1;
      add(panel, c);
    }
    else {
      throw new UnsupportedOperationException(cell.getClass().toString());
    }
    if (index >= 0) {
      myIpnbPanels.remove(index);
      myIpnbPanels.add(index, panel);
    }
    setSelectedCell(panel);
    remove(from);
    revalidate();
    repaint();
  }

  private void addComponent(@NotNull final GridBagConstraints c, @NotNull final IpnbEditablePanel comp) {
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
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        mySelectedCell.switchToEditing();
      }
      if (e.getKeyCode() == KeyEvent.VK_UP) {
        selectPrev(mySelectedCell);
      }

      else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
        selectNext(mySelectedCell);
      }
    }
  }

  private void selectPrev(@NotNull IpnbEditablePanel cell) {
    int index = myIpnbPanels.indexOf(cell);
    if (index > 0) {
      setSelectedCell(myIpnbPanels.get(index - 1));
    }
  }

  public void selectNext(@NotNull IpnbEditablePanel cell) {
    int index = myIpnbPanels.indexOf(cell);
    if (index < myIpnbPanels.size() - 1) {
      setSelectedCell(myIpnbPanels.get(index + 1));
    }
  }

  public void selectNextOrPrev(@NotNull IpnbEditablePanel cell) {
    int index = myIpnbPanels.indexOf(cell);
    if (index < myIpnbPanels.size() - 1) {
      setSelectedCell(myIpnbPanels.get(index + 1));
    }
    else if (index > 0) {
      setSelectedCell(myIpnbPanels.get(index - 1));
    }
    else {
      mySelectedCell = null;
      repaint();
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (mySelectedCell != null) {
      g.setColor(mySelectedCell.isEditing() ? JBColor.GREEN : JBColor.GRAY);
      g.drawRoundRect(100, mySelectedCell.getTop() - 1, getWidth() - 200, mySelectedCell.getHeight() + 2, 5, 5);
    }
  }

  private void updateCellSelection(MouseEvent e) {
    if (e.getClickCount() > 0) {
      IpnbEditablePanel ipnbPanel = getIpnbPanelByClick(e.getPoint());
      if (ipnbPanel != null) {
        ipnbPanel.setEditing(false);
        ipnbPanel.requestFocus();
        repaint();
        setSelectedCell(ipnbPanel);
      }
    }
  }

  public void setSelectedCell(@NotNull final IpnbEditablePanel ipnbPanel) {
    if (ipnbPanel.equals(mySelectedCell)) return;
    if (mySelectedCell != null)
      mySelectedCell.setEditing(false);
    mySelectedCell = ipnbPanel;
    requestFocus();
    repaint();
    myListener.selectionChanged(ipnbPanel);
  }

  public IpnbEditablePanel getSelectedCell() {
    return mySelectedCell;
  }

  @Nullable
  private IpnbEditablePanel getIpnbPanelByClick(@NotNull final Point point) {
    for (IpnbEditablePanel c: myIpnbPanels) {
      if (c.contains(point.y)) {
        return c;
      }
    }
    return null;
  }

  public IpnbFile getIpnbFile() {
    return myIpnbFile;
  }
}
