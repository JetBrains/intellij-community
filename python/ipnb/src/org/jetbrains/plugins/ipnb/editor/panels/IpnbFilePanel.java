package org.jetbrains.plugins.ipnb.editor.panels;

import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.IpnbParser;
import org.jetbrains.plugins.ipnb.format.cells.*;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IpnbFilePanel extends JPanel implements Scrollable, DataProvider {

  public static final int INSET_Y = 10;
  public static final int INSET_X = 5;
  private IpnbFile myIpnbFile;

  private Project myProject;
  @NotNull private IpnbFileEditor myParent;
  @NotNull private final IpnbFileEditor.CellSelectionListener myListener;

  private final List<IpnbEditablePanel> myIpnbPanels = Lists.newArrayList();

  private IpnbEditablePanel mySelectedCell;
  private IpnbEditablePanel myBufferPanel;
  private int myIncrement = 10;
  private int myInitialSelection = 0;
  private int myInitialPosition = 0;

  public IpnbFilePanel(@NotNull final Project project, @NotNull final IpnbFileEditor parent, @NotNull final VirtualFile vFile,
                       @NotNull final IpnbFileEditor.CellSelectionListener listener) {
    super(new GridBagLayout());
    myProject = project;
    myParent = parent;
    myListener = listener;
    setBackground(IpnbEditorUtil.getBackground());

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          myIpnbFile = IpnbParser.parseIpnbFile(vFile);
          layoutFile();
          addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              updateCellSelection(e);
            }
          });
          setFocusable(true);

        }
        catch (IOException e) {
          Messages.showErrorDialog(project, e.getMessage(), "Can't open " + vFile.getPath());
        }
      }
    });

    UIUtil.requestFocus(this);
  }

  public List<IpnbEditablePanel> getIpnbPanels() {
    return myIpnbPanels;
  }

  private void layoutFile() {
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 2;
    c.insets = new Insets(INSET_Y, INSET_X, 0, 0);

    final int width = IpnbEditorUtil.PANEL_WIDTH + IpnbEditorUtil.PROMPT_SIZE.width;
    final JLabel label = new JLabel("<html><body style='width: " + width + "px'></body></html>");
    add(label, c);

    c.gridy = 1;
    c.gridwidth = 1;
    final JPanel panel = new JPanel();
    panel.setPreferredSize(IpnbEditorUtil.PROMPT_SIZE);
    panel.setBackground(getBackground());
    panel.setOpaque(false);
    add(panel, c);

    final List<IpnbCell> cells = myIpnbFile.getCells();
    for (IpnbCell cell : cells) {
      c.gridy = addCellToPanel(cell, c);
    }

    if (myInitialSelection >= 0 && myIpnbPanels.size() > myInitialSelection) {
      final IpnbEditablePanel toSelect = myIpnbPanels.get(myInitialSelection);
      setSelectedCell(toSelect);
      myParent.getScrollPane().getViewport().setViewPosition(new Point(0, myInitialPosition));
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
    return c.gridy + 1;
  }

  public void createAndAddCell() {
    removeAll();
    final IpnbCodeCell cell = new IpnbCodeCell("python", new String[]{""}, null, new ArrayList<IpnbOutputCell>());
    final IpnbCodePanel codePanel = new IpnbCodePanel(myProject, myParent, cell);

    addCell(cell, codePanel);
  }

  private void addCell(IpnbEditableCell cell, IpnbEditablePanel panel) {
    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 1;
    c.insets = new Insets(INSET_Y, INSET_X, 0, 0);

    if (myIpnbPanels.isEmpty()) {
      final int width = IpnbEditorUtil.PANEL_WIDTH + IpnbEditorUtil.PROMPT_SIZE.width;
      final JLabel label = new JLabel("<html><body style='width: " + width + "px'></body></html>");
      add(label, c);
    }
    final IpnbEditablePanel selectedCell = getSelectedCell();
    final int index = myIpnbPanels.indexOf(selectedCell);
    myIpnbFile.addCell(cell, index + 1);
    myIpnbPanels.add(index + 1, panel);


    final JPanel promptPanel = new JPanel();
    promptPanel.setPreferredSize(new Dimension(IpnbEditorUtil.PROMPT_SIZE.width, 1));
    promptPanel.setBackground(getBackground());
    promptPanel.setOpaque(false);
    add(promptPanel, c);

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
    requestFocus();
    revalidate();
    repaint();
  }

  public void cutCell() {
    myBufferPanel = getSelectedCell();
    if (myBufferPanel == null) return;
    selectNextOrPrev(myBufferPanel);
    final int index = myIpnbPanels.indexOf(myBufferPanel);
    if (index < 0) return;
    myIpnbPanels.remove(index);
    myIpnbFile.removeCell(index);

    remove(myBufferPanel);
    if (myIpnbPanels.isEmpty()) {
      createAndAddCell();
    }
  }

  public void copyCell() {
    myBufferPanel = getSelectedCell();
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
    if (from instanceof IpnbCodePanel) {
      panel.switchToEditing();
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

  private static JPanel createEmptyPanel() {
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
      int index = myIpnbPanels.indexOf(mySelectedCell);
      final Rectangle rect = getVisibleRect();


      if (e.getKeyCode() == KeyEvent.VK_UP) {
        selectPrev(mySelectedCell);
        if (index > 0) {
          final Rectangle cellBounds = mySelectedCell.getBounds();
          if (cellBounds.getY() <= rect.getY()) {
            myIncrement = rect.y - cellBounds.y;
            getParent().dispatchEvent(e);
          }
        }
      }
      else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
        selectNext(mySelectedCell);
        if (index < myIpnbPanels.size() - 1) {
          final Rectangle cellBounds = mySelectedCell.getBounds();
          if (cellBounds.getY() + cellBounds.getHeight() > rect.getY() + rect.getHeight()) {
            myIncrement = cellBounds.y + cellBounds.height - rect.y - rect.height;
            getParent().dispatchEvent(e);
          }
        }
      }
      else {
        getParent().dispatchEvent(e);
      }
    }
  }

  public void selectPrev(@NotNull IpnbEditablePanel cell) {
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

  public void setInitialPosition(int index, int position) {
    myInitialSelection = index;
    myInitialPosition = position;
  }

  public void setSelectedCell(@NotNull final IpnbEditablePanel ipnbPanel) {
    if (ipnbPanel.equals(mySelectedCell)) return;
    if (mySelectedCell != null)
      mySelectedCell.setEditing(false);
    mySelectedCell = ipnbPanel;
    revalidate();
    UIUtil.requestFocus(this);
    repaint();
    myListener.selectionChanged(ipnbPanel);
  }

  public IpnbEditablePanel getSelectedCell() {
    return mySelectedCell;
  }

  public int getSelectedIndex() {
    final IpnbEditablePanel selectedCell = getSelectedCell();
    return myIpnbPanels.indexOf(selectedCell);
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

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return null;
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return myIncrement;

  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 100;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return false;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    final IpnbEditablePanel cell = getSelectedCell();
    if (OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
      if (cell instanceof IpnbCodePanel) {
        return ((IpnbCodePanel)cell).getEditor();
      }
    }
    return null;
  }
}
