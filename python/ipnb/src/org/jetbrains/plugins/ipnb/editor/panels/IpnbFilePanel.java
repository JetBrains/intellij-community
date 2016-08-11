package org.jetbrains.plugins.ipnb.editor.panels;

import com.google.common.collect.Lists;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.JBColor;
import com.intellij.util.Alarm;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbCutCellAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbDeleteCellAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbPasteCellAction;
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
import java.util.Collections;
import java.util.List;

public class IpnbFilePanel extends JPanel implements Scrollable, DataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(IpnbFilePanel.class);
  private final DocumentAdapter myDocumentListener;
  private Document myDocument;
  private IpnbFile myIpnbFile;
  private Project myProject;
  @NotNull private IpnbFileEditor myParent;
  @NotNull private final VirtualFile myVirtualFile;
  @NotNull private final IpnbFileEditor.CellSelectionListener myListener;

  private final List<IpnbEditablePanel> myIpnbPanels = Lists.newArrayList();

  @Nullable private IpnbEditablePanel mySelectedCell;
  boolean switchToEditing = false;
  private IpnbEditablePanel myBufferPanel;
  private int myInitialSelection = 0;

  public IpnbFilePanel(@NotNull final Project project, @NotNull final IpnbFileEditor parent, @NotNull final VirtualFile vFile,
                       @NotNull final IpnbFileEditor.CellSelectionListener listener) {
    super(new VerticalFlowLayout(VerticalFlowLayout.TOP, 100, 5, true, false));
    myProject = project;
    myParent = parent;
    myVirtualFile = vFile;
    myListener = listener;
    setBackground(IpnbEditorUtil.getBackground());

    final Alarm alarm = new Alarm();
    myDocumentListener = new DocumentAdapter() {
      public void documentChanged(final DocumentEvent e) {
        alarm.cancelAllRequests();
        alarm.addRequest(new MySynchronizeRequest(), 10, ModalityState.stateForComponent(IpnbFilePanel.this));
      }
    };
    myDocument = myParent.getDocument();
    myDocument.addDocumentListener(myDocumentListener);

    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
        readFromFile(true);
        addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            updateCellSelection(e);
          }
        });
        setFocusable(true);
      }
    }, 10, ModalityState.stateForComponent(this));

    UIUtil.requestFocus(this);
  }

  public void dispose() {
    myDocument.removeDocumentListener(myDocumentListener);
  }

  private void readFromFile(boolean showError) {
    try {
      removeAll();
      myIpnbFile = IpnbParser.parseIpnbFile(myDocument, myVirtualFile);
      myIpnbPanels.clear();
      mySelectedCell = null;
      if (myIpnbFile.getCells().isEmpty()) {
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                createAndAddCell(true);
                saveToFile();
              }
            });
          }
        });

      }
    } catch (IOException e) {
      if (showError)
        Messages.showErrorDialog(getProject(), e.getMessage(), "Can't open " + myVirtualFile.getPath());
      else
        LOG.error(e.getMessage(), "Can't open " + myVirtualFile.getPath());

    }
    layoutFile();
  }

  public List<IpnbEditablePanel> getIpnbPanels() {
    return myIpnbPanels;
  }

  private void layoutFile() {
    addWarningIfNeeded();
    final List<IpnbCell> cells = myIpnbFile.getCells();
    for (IpnbCell cell : cells) {
      addCellToPanel(cell);
    }

    if (myInitialSelection >= 0 && myIpnbPanels.size() > myInitialSelection) {
      final IpnbEditablePanel toSelect = myIpnbPanels.get(myInitialSelection);
      setSelectedCell(toSelect);
      if (switchToEditing) {
        toSelect.switchToEditing();
        switchToEditing = false;
      }
    }
    add(createEmptyPanel());
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (mySelectedCell != null)
          myParent.updateScrollPosition(mySelectedCell);
      }
    });
  }

  private void addWarningIfNeeded() {
    if (IpnbUtils.hasFx()) return;
    final String text;
    final String href;
    if (PlatformUtils.isPyCharm()) {
      href = "https://www.jetbrains.com/pycharm/download/";
      text = "<html><a href=\"https://www.jetbrains.com/pycharm/download/\">Download PyCharm</a> with bundled JDK for better " +
             "Markdown cell rendering</html>";
    }
    else {
      href = "https://confluence.jetbrains.com/display/PYH/Pycharm+2016.1+Jupyter+Notebook+rendering";
      text = "<html>Follow instructions <a href=\"https://confluence.jetbrains.com/display/PYH/Pycharm+2016.1+Jupyter+Notebook+rendering\">" +
             "here</a> for better Markdown cell rendering</html>";
    }
    final JLabel warning = new JLabel(text, SwingConstants.CENTER);
    warning.setForeground(JBColor.RED);
    warning.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        BrowserUtil.browse(href);
      }
    });
    add(warning);
  }

  private void addCellToPanel(IpnbCell cell) {
    IpnbEditablePanel panel;
    if (cell instanceof IpnbCodeCell) {
      panel = new IpnbCodePanel(myProject, myParent, (IpnbCodeCell)cell);
      add(panel);
      myIpnbPanels.add(panel);
    }
    else if (cell instanceof IpnbMarkdownCell) {
      panel = new IpnbMarkdownPanel((IpnbMarkdownCell)cell, this);
      addComponent(panel);
    }
    else if (cell instanceof IpnbHeadingCell) {
      panel = new IpnbHeadingPanel((IpnbHeadingCell)cell);
      addComponent(panel);
    }
    else if (cell instanceof IpnbRawCell) {
      // A raw cell is defined as content that should be included unmodified in nbconvert output.
      // It's not visible for user
    }
    else {
      throw new UnsupportedOperationException(cell.getClass().toString());
    }
  }

  public void createAndAddCell(final boolean below) {
    final IpnbCodeCell cell = new IpnbCodeCell("python", Collections.emptyList(), null, new ArrayList<>(),
                                               null);
    final IpnbCodePanel codePanel = new IpnbCodePanel(myProject, myParent, cell);

    addCell(codePanel, below);
  }

  private void addCell(@NotNull final IpnbEditablePanel panel, boolean below) {
    final IpnbEditablePanel selectedCell = getSelectedCell();
    int index = myIpnbPanels.indexOf(selectedCell);
    if (below) {
      index += 1;
    }
    final IpnbEditableCell cell = panel.getCell();
    myIpnbFile.addCell(cell, index);
    myIpnbPanels.add(index, panel);
    setSelectedCell(panel);
  }

  public void cutCell() {
    myBufferPanel = getSelectedCell();
    if (myBufferPanel == null) return;
    deleteSelectedCell();
  }

  public void moveCell(boolean down) {
    final IpnbEditablePanel selectedCell = getSelectedCell();
    if (selectedCell == null) return;

    final int index = getSelectedIndex();
    int siblingIndex = down ? index + 1 : index - 1;

    if (myIpnbPanels.size() <= siblingIndex && down) {
      return;
    }
    if (siblingIndex < 0 && !down) {
      return;
    }

    if (down) {
      deleteSelectedCell();
      final IpnbEditableCell cell = selectedCell.getCell();
      myIpnbFile.addCell(cell, index + 1);
      myIpnbPanels.add(index + 1, selectedCell);
      selectPrev(selectedCell);
      setSelectedCell(selectedCell);
    }
    else {
      final IpnbEditablePanel siblingPanel = myIpnbPanels.get(siblingIndex);
      deleteCell(siblingPanel);
      addCell(siblingPanel, true);
      setSelectedCell(selectedCell);
    }
    saveToFile();

  }

  public void deleteSelectedCell() {
    final IpnbEditablePanel cell = getSelectedCell();
    if (cell != null)
      deleteCell(cell);
  }

  private boolean deleteCell(@NotNull final IpnbEditablePanel cell) {
    final int index = myIpnbPanels.indexOf(cell);
    if (index < 0) return true;
    myIpnbPanels.remove(index);
    myIpnbFile.removeCell(index);
    return false;
  }

  public void saveToFile() {
    final String oldText = myDocument.getText();
    final String newText = IpnbParser.newDocumentText(this);
    if (newText == null) return;
    if (oldText.equals(newText)) {
      new Alarm().addRequest(new MySynchronizeRequest(), 10, ModalityState.stateForComponent(this));
      return;
    }
    try {
      final ReplaceInfo replaceInfo = findFragmentToChange(oldText, newText);
      if (replaceInfo.getStartOffset() != -1) {
        myDocument.replaceString(replaceInfo.getStartOffset(), replaceInfo.getEndOffset(), replaceInfo.getReplacement());
      }
    }
    catch (Exception e) {
      myDocument.replaceString(0, oldText.length(), newText);
    }
  }

  public static final class ReplaceInfo {
    private final int myStartOffset;
    private final int myEndOffset;
    private final String myReplacement;

    public ReplaceInfo(final int startOffset, final int endOffset, final String replacement) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myReplacement = replacement;
    }

    public int getStartOffset() {
      return myStartOffset;
    }

    public int getEndOffset() {
      return myEndOffset;
    }

    public String getReplacement() {
      return myReplacement;
    }
  }

  public static ReplaceInfo findFragmentToChange(@NotNull final String oldText, @NotNull final String newText) {
    if (oldText.equals(newText)) {
      return new ReplaceInfo(-1, -1, null);
    }

    final int oldLength = oldText.length();
    final int newLength = newText.length();

    int startOffset = 0;
    while (
      startOffset < oldLength && startOffset < newLength &&
      oldText.charAt(startOffset) == newText.charAt(startOffset)
      ) {
      startOffset++;
    }

    int endOffset = oldLength;
    while (true) {
      if (endOffset <= startOffset) {
        break;
      }
      final int idxInNew = newLength - (oldLength - endOffset) - 1;
      if (idxInNew < startOffset) {
        break;
      }

      final char c1 = oldText.charAt(endOffset - 1);
      final char c2 = newText.charAt(idxInNew);
      if (c1 != c2) {
        break;
      }
      endOffset--;
    }

    return new ReplaceInfo(startOffset, endOffset, newText.substring(startOffset, newLength - (oldLength - endOffset)));
  }

  private class MySynchronizeRequest implements Runnable {

    public void run() {
      final Project project = getProject();
      if (project.isDisposed()) {
        return;
      }
      if (Disposer.isDisposed(myParent))
        return;
      PsiDocumentManager.getInstance(project).commitDocument(myDocument);
      final IpnbEditablePanel selectedCell = getSelectedCell();
      final int index = myIpnbPanels.indexOf(selectedCell);
      myInitialSelection = index >= 0 && index < myIpnbPanels.size() ? index : myIpnbPanels.size() - 1;
      readFromFile(false);
    }
  }


  public void copyCell() {
    myBufferPanel = getSelectedCell();
  }

  public void pasteCell() {
    if (myBufferPanel == null) return;
    final IpnbEditablePanel editablePanel = (IpnbEditablePanel)myBufferPanel.clone();
    addCell(editablePanel, true);
  }

  public void replaceComponent(@NotNull final IpnbEditablePanel from, @NotNull final IpnbCell cell) {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final int index = myIpnbPanels.indexOf(from);
            IpnbEditablePanel panel;
            if (cell instanceof IpnbCodeCell) {
              panel = new IpnbCodePanel(myProject, myParent, (IpnbCodeCell)cell);
            }
            else if (cell instanceof IpnbMarkdownCell) {
              panel = new IpnbMarkdownPanel((IpnbMarkdownCell)cell, myParent.getIpnbFilePanel());
            }
            else if (cell instanceof IpnbHeadingCell) {
              panel = new IpnbHeadingPanel((IpnbHeadingCell)cell);
            }
            else {
              throw new UnsupportedOperationException(cell.getClass().toString());
            }
            if (index >= 0) {
              myIpnbFile.removeCell(index);
              myIpnbFile.addCell(cell, index);
              myIpnbPanels.remove(index);
              myIpnbPanels.add(index, panel);
            }

            if (from instanceof IpnbCodePanel) {
              switchToEditing = true;
            }
            setSelectedCell(panel);
            saveToFile();
          }
        });
      }
    }, "Ipnb.changeCellType", new Object());

  }

  private void addComponent(@NotNull final IpnbEditablePanel comp) {
    add(comp);
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
        repaint();
      }

      if (e.getKeyCode() == KeyEvent.VK_UP) {
        selectPrev(mySelectedCell);
      }
      else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
        selectNext(mySelectedCell);
      }
      else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
        if (!mySelectedCell.isEditing()) {
          IpnbDeleteCellAction.deleteCell(this);
        }
      }
      else if (e.getModifiers() == Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) {
        if (e.getKeyCode() == KeyEvent.VK_X) {
          if (!mySelectedCell.isEditing()) {
            IpnbCutCellAction.cutCell(this);
          }
        }
        else if (e.getKeyCode() == KeyEvent.VK_C) {
          if (!mySelectedCell.isEditing()) {
            copyCell();
          }
        }
        else if (e.getKeyCode() == KeyEvent.VK_V) {
          if (!mySelectedCell.isEditing()) {
            IpnbPasteCellAction.pasteCell(this);
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
      g.drawRoundRect(mySelectedCell.getX() - 50, mySelectedCell.getTop() - 1,
                      mySelectedCell.getWidth() + 145 - IpnbEditorUtil.PROMPT_SIZE.width, mySelectedCell.getHeight() + 2, 5, 5);
    }
  }

  private void updateCellSelection(MouseEvent e) {
    if (e.getClickCount() > 0) {
      IpnbEditablePanel ipnbPanel = getIpnbPanelByClick(e.getPoint());
      if (ipnbPanel != null) {
        ipnbPanel.setEditing(false);
        ipnbPanel.requestFocus();
        repaint();
        setSelectedCell(ipnbPanel, true);
      }
    }
  }

  public void setInitialPosition(int index) {
    myInitialSelection = index;
  }

  public void setSelectedCell(@NotNull final IpnbEditablePanel ipnbPanel) {
    setSelectedCell(ipnbPanel, false);
  }

  public void setSelectedCell(@NotNull final IpnbEditablePanel ipnbPanel, boolean mouse) {
    if (ipnbPanel.equals(mySelectedCell)) return;
    if (mySelectedCell != null)
      mySelectedCell.setEditing(false);
    mySelectedCell = ipnbPanel;
    revalidate();
    UIUtil.requestFocus(this);
    repaint();
    if (ipnbPanel.getBounds().getHeight() != 0) {
      myListener.selectionChanged(ipnbPanel, mouse);
    }
  }

  @Nullable
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
    return 10;

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
    if (IpnbFileEditor.DATA_KEY.is(dataId)) {
      return myParent;
    }
    return null;
  }

  public Project getProject() {
    return myProject;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public Document getDocument() {
    return myDocument;
  }
}
