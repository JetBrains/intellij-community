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
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.JBColor;
import com.intellij.util.Alarm;
import com.intellij.util.PlatformUtils;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.IpnbUtils;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbCutCellAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbDeleteCellAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbPasteCellAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbToggleLineNumbersAction;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.IpnbFile;
import org.jetbrains.plugins.ipnb.format.IpnbParser;
import org.jetbrains.plugins.ipnb.format.cells.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class IpnbFilePanel extends JPanel implements Scrollable, DataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(IpnbFilePanel.class);
  private final DocumentListener myDocumentListener;
  private final Document myDocument;
  private final MessageBusConnection myBusConnection;
  private IpnbFile myIpnbFile;
  private final Project myProject;
  @NotNull private final IpnbFileEditor myParent;
  @NotNull private final VirtualFile myVirtualFile;
  @NotNull private final IpnbFileEditor.CellSelectionListener myListener;

  private final List<IpnbEditablePanel> myIpnbPanels = Lists.newArrayList();

  @Nullable private IpnbEditablePanel mySelectedCellPanel;
  private IpnbEditablePanel myBufferPanel;
  private int myInitialSelection = 0;
  private boolean mySynchronize;

  public IpnbFilePanel(@NotNull final Project project, @NotNull final IpnbFileEditor parent, @NotNull final VirtualFile vFile,
                       @NotNull final IpnbFileEditor.CellSelectionListener listener) {
    super(new VerticalFlowLayout(VerticalFlowLayout.TOP, 100, 5, true, false));
    myProject = project;
    myParent = parent;
    myVirtualFile = vFile;
    myListener = listener;
    setBackground(IpnbEditorUtil.getBackground());

    final Alarm alarm = new Alarm();
    myDocumentListener = new DocumentListener() {
      public void documentChanged(final DocumentEvent e) {
        if (mySynchronize) {
          alarm.cancelAllRequests();
          alarm.addRequest(new MySynchronizeRequest(), 10, ModalityState.stateForComponent(IpnbFilePanel.this));
        }
        mySynchronize = true;
      }
    };
    myDocument = myParent.getDocument();
    myDocument.addDocumentListener(myDocumentListener);

    alarm.addRequest(() -> {
      readFromFile(true);
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          updateCellSelection(e);
        }
      });
      setFocusable(true);
    }, 10, ModalityState.stateForComponent(this));

    UIUtil.requestFocus(this);
    myBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
    myBusConnection.subscribe(ProjectEx.ProjectSaved.TOPIC,
                              new ProjectEx.ProjectSaved() {
                                @Override
                                public void saved(@NotNull Project project) {
                                  CommandProcessor.getInstance().runUndoTransparentAction(
                                    () -> ApplicationManager.getApplication()
                                      .runWriteAction(() -> saveToFile(false)));
                                }
                              });
  }

  @Override
  public void dispose() {
    myDocument.removeDocumentListener(myDocumentListener);
    Disposer.dispose(myBusConnection);
  }

  private void readFromFile(boolean showError) {
    try {
      removeAll();
      myIpnbFile = IpnbParser.parseIpnbFile(myDocument, myVirtualFile);
      myIpnbPanels.clear();
      mySelectedCellPanel = null;
      if (myIpnbFile.getCells().isEmpty()) {
        CommandProcessor.getInstance().runUndoTransparentAction(() -> ApplicationManager.getApplication().runWriteAction(() -> {
          createAndAddCell(true, IpnbCodeCell.createEmptyCodeCell());
          saveToFile(true);
        }));
      }
    }
    catch (IOException e) {
      if (showError) {
        Messages.showErrorDialog(getProject(), e.getMessage(), "Can't open " + myVirtualFile.getPath());
      }
      else {
        LOG.error(e.getMessage(), "Can't open " + myVirtualFile.getPath());
      }
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
      setSelectedCellPanel(toSelect);
    }
    add(createEmptyPanel());
    ApplicationManager.getApplication().invokeLater(() -> {
      if (mySelectedCellPanel != null) {
        myParent.updateScrollPosition(mySelectedCellPanel);
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
      text =
        "<html>Follow instructions <a href=\"https://confluence.jetbrains.com/display/PYH/Pycharm+2016.1+Jupyter+Notebook+rendering\">" +
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
      addComponent(panel);
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

  public void createAndAddCell(final boolean below, IpnbCodeCell cell) {
    final IpnbCodePanel codePanel = new IpnbCodePanel(myProject, myParent, cell);
    addCell(codePanel, below);
  }

  private void addCell(@NotNull final IpnbEditablePanel panel, boolean below) {
    final IpnbEditablePanel selectedCellPanel = getSelectedCellPanel();
    int index = myIpnbPanels.indexOf(selectedCellPanel);
    if (below) {
      index += 1;
    }
    final IpnbEditableCell cell = panel.getCell();
    myIpnbFile.addCell(cell, index);
    myIpnbPanels.add(index, panel);
    add(panel, index);
    setSelectedCellPanel(panel);
  }

  public void cutCell() {
    myBufferPanel = getSelectedCellPanel();
    if (myBufferPanel == null) return;
    deleteSelectedCell();
  }

  public void moveCell(boolean down) {
    final IpnbEditablePanel selectedCellPanel = getSelectedCellPanel();
    if (selectedCellPanel == null) return;

    final int index = getSelectedIndex();
    int siblingIndex = down ? index + 1 : index - 1;

    if (myIpnbPanels.size() <= siblingIndex && down) {
      return;
    }
    if (siblingIndex < 0 && !down) {
      return;
    }

    if (down) {
      final IpnbEditableCell cell = selectedCellPanel.getCell();
      deleteSelectedCell();
      myIpnbFile.addCell(cell, index + 1);
      myIpnbPanels.add(index + 1, selectedCellPanel);
      add(selectedCellPanel, index + 1);

      selectPrev(selectedCellPanel);
      setSelectedCellPanel(selectedCellPanel);
    }
    else {
      final IpnbEditablePanel siblingPanel = myIpnbPanels.get(siblingIndex);
      deleteCell(siblingPanel);
      addCell(siblingPanel, true);
      setSelectedCellPanel(selectedCellPanel);
    }
    saveToFile(false);
  }

  public void mergeCell(boolean below) {
    final IpnbEditablePanel currentCellPanel = getSelectedCellPanel();
    if (currentCellPanel == null) return;

    if (below) {
      selectNext(currentCellPanel, false);
    }
    else {
      selectPrev(currentCellPanel);
    }

    final IpnbEditablePanel cellToMergePanel = getSelectedCellPanel();
    final IpnbCell cellToMerge = cellToMergePanel.getCell();
    final List<String> currentCellSource = getCellSource(currentCellPanel);
    final List<String> cellToMergeSource = ((IpnbEditableCell)cellToMerge).getSource();
    final ArrayList<String> source = mergeCellsSource(currentCellSource, cellToMergeSource, below);
    ((IpnbEditableCell)cellToMerge).setSource(source);
    cellToMergePanel.updateCellView();

    actualizeCellData(cellToMerge);

    currentCellPanel.repaint();
    deleteCell(currentCellPanel);
    saveToFile(false);
  }

  private static ArrayList<String> mergeCellsSource(@NotNull List<String> currentCellSource,
                                                    @NotNull List<String> cellToMergeSource,
                                                    boolean below) {
    final ArrayList<String> source = new ArrayList<>();
    if (below) {
      source.addAll(currentCellSource);
      source.add("\n");
      source.addAll(cellToMergeSource);
    }
    else {
      source.addAll(cellToMergeSource);
      source.add("\n");
      source.addAll(currentCellSource);
    }
    return source;
  }

  private static void actualizeCellData(@NotNull IpnbCell cell) {
    if (cell instanceof IpnbCodeCell) {
      ((IpnbCodeCell)cell).removeCellOutputs();
      ((IpnbCodeCell)cell).setPromptNumber(null);
    }
  }

  public void splitCell() {
    final IpnbEditablePanel selectedCellPanel = getSelectedCellPanel();
    if (selectedCellPanel == null) return;

    final IpnbEditableCell cell = selectedCellPanel.getCell();
    final int position = selectedCellPanel.getCaretPosition();

    if (position == -1) return;

    final String oldCellText = selectedCellPanel.getText(0, position);
    final String newCellText = selectedCellPanel.getText(position);

    if (oldCellText != null) {
      final JTextArea editablePanel = selectedCellPanel.getEditableTextArea();
      if (editablePanel != null) {
        editablePanel.setText(oldCellText);
      }
      selectedCellPanel.getCell().setSource(createCellSourceFromText(oldCellText));
      actualizeCellData(cell);
      selectedCellPanel.updateCellView();
    }

    IpnbEditablePanel panel;
    final ArrayList<String> newCellSource = createCellSourceFromText(newCellText);
    panel = createPanel(cell, newCellSource);
    addCell(panel, true);

    saveToFile(false);
  }

  @NotNull
  private IpnbEditablePanel createPanel(@NotNull IpnbEditableCell cell, @NotNull ArrayList<String> newCellSource) {
    if (cell instanceof IpnbCodeCell) {
      final IpnbCodeCell codeCell = (IpnbCodeCell)cell;
      final IpnbCodeCell ipnbCodeCell = new IpnbCodeCell(codeCell.getLanguage(), newCellSource, codeCell.getPromptNumber(),
                                                         codeCell.getCellOutputs(), codeCell.getMetadata());
      return new IpnbCodePanel(myProject, myParent, ipnbCodeCell);
    }
    else if (cell instanceof IpnbMarkdownCell) {
      final IpnbMarkdownCell markdownCell = new IpnbMarkdownCell(newCellSource, cell.getMetadata());
      return new IpnbMarkdownPanel(markdownCell, this);
    }
    else {
      final IpnbHeadingCell headingCell = new IpnbHeadingCell(newCellSource, ((IpnbHeadingCell)cell).getLevel(), cell.getMetadata());
      return new IpnbHeadingPanel(headingCell);
    }
  }

  private static ArrayList<String> createCellSourceFromText(@NotNull String oldCellText) {
    final ArrayList<String> source = new ArrayList<>();
    source.addAll(Arrays.stream(oldCellText.split("\n")).map(s -> s + "\n").collect(Collectors.toList()));
    return source;
  }

  @NotNull
  private static List<String> getCellSource(@NotNull IpnbEditablePanel cellPanel) {
    final IpnbCell cell = cellPanel.getCell();
    return ((IpnbEditableCell)cell).getSource();
  }

  public void deleteSelectedCell() {
    final IpnbEditablePanel selectedCellPanel = getSelectedCellPanel();
    if (selectedCellPanel != null) {
      deleteCell(selectedCellPanel);
    }
  }

  private void deleteCell(@NotNull final IpnbEditablePanel cell) {
    final int index = myIpnbPanels.indexOf(cell);
    if (index < 0) return;
    myIpnbPanels.remove(index);
    myIpnbFile.removeCell(index);
    remove(index);

    if (!myIpnbPanels.isEmpty()) {
      int indexToSelect = index < myIpnbPanels.size() ? index : index - 1;
      final IpnbEditablePanel panel = myIpnbPanels.get(indexToSelect);
      setSelectedCell(panel, false);
    }
  }

  public void saveToFile(boolean synchronize) {
    mySynchronize = synchronize;
    final String oldText = myDocument.getText();
    final String newText = IpnbParser.newDocumentText(this);
    if (newText == null) return;
    if (oldText.equals(newText) && mySynchronize) {
      new Alarm().addRequest(new MySynchronizeRequest(), 10, ModalityState.stateForComponent(this));
      mySynchronize = false;
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
      if (Disposer.isDisposed(myParent)) {
        return;
      }
      PsiDocumentManager.getInstance(project).commitDocument(myDocument);
      final IpnbEditablePanel selectedCellPanel = getSelectedCellPanel();
      final int index = myIpnbPanels.indexOf(selectedCellPanel);
      myInitialSelection = index >= 0 && index < myIpnbPanels.size() ? index : myIpnbPanels.size() - 1;
      readFromFile(false);
    }
  }


  public void copyCell() {
    myBufferPanel = getSelectedCellPanel();
  }

  public void pasteCell() {
    if (myBufferPanel == null) return;
    final IpnbEditablePanel editablePanel = (IpnbEditablePanel)myBufferPanel.clone();
    addCell(editablePanel, true);
  }

  public void replaceComponent(@NotNull final IpnbEditablePanel from, @NotNull final IpnbCell cell) {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
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
        myIpnbPanels.remove(index);
        remove(index);

        myIpnbFile.addCell(cell, index);
        myIpnbPanels.add(index, panel);
        add(panel, index);
      }

      if (from instanceof IpnbCodePanel) {
        panel.switchToEditing();
      }
      setSelectedCellPanel(panel);
      saveToFile(false);
    }), "Ipnb.changeCellType", new Object());
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
    if (mySelectedCellPanel != null && e.getID() == KeyEvent.KEY_PRESSED) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        mySelectedCellPanel.switchToEditing();
        repaint();
      }
      if (e.getKeyCode() == KeyEvent.VK_UP) {
        selectPrev(mySelectedCellPanel);
      }
      else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
        selectNext(mySelectedCellPanel, false);
      }
      else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
        if (!mySelectedCellPanel.isEditing()) {
          IpnbDeleteCellAction.deleteCell(this);
        }
      }
      else if (e.getKeyCode() == KeyEvent.VK_L) {
        if (mySelectedCellPanel instanceof IpnbCodePanel && !mySelectedCellPanel.isEditing()) {
          IpnbToggleLineNumbersAction.toggleLineNumbers((IpnbCodePanel)mySelectedCellPanel);
        }
      }
      else if (e.getModifiers() == Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) {
        if (e.getKeyCode() == KeyEvent.VK_X) {
          if (!mySelectedCellPanel.isEditing()) {
            IpnbCutCellAction.cutCell(this);
          }
        }
        else if (e.getKeyCode() == KeyEvent.VK_C) {
          if (!mySelectedCellPanel.isEditing()) {
            copyCell();
          }
        }
        else if (e.getKeyCode() == KeyEvent.VK_V) {
          if (!mySelectedCellPanel.isEditing()) {
            IpnbPasteCellAction.pasteCell(this);
          }
        }
      }
      else {
        getParent().dispatchEvent(e);
      }
    }
  }

  public boolean hasNextCell(@NotNull IpnbEditablePanel cell) {
    int index = myIpnbPanels.indexOf(cell);
    return index < myIpnbPanels.size() - 1;
  }

  public boolean hasPrevCell(@NotNull IpnbEditablePanel cell) {
    int index = myIpnbPanels.indexOf(cell);
    return index > 0;
  }

  public void selectPrev(@NotNull IpnbEditablePanel cell) {
    int index = myIpnbPanels.indexOf(cell);
    if (index > 0) {
      setSelectedCellPanel(myIpnbPanels.get(index - 1));
    }
  }

  public void selectNext(@NotNull IpnbEditablePanel cell, boolean addNew) {
    int index = myIpnbPanels.indexOf(cell);
    if (index < myIpnbPanels.size() - 1) {
      setSelectedCellPanel(myIpnbPanels.get(index + 1));
    }
    else if (addNew) {
      createAndAddCell(true, IpnbCodeCell.createEmptyCodeCell());
      CommandProcessor.getInstance().executeCommand(getProject(),
                                                    () -> ApplicationManager.getApplication().runWriteAction(
                                                      () -> saveToFile(false)), "Ipnb.runCell", new Object());
    }
  }

  public void selectNextOrPrev(@NotNull IpnbEditablePanel cell) {
    int index = myIpnbPanels.indexOf(cell);
    if (index < myIpnbPanels.size() - 1) {
      setSelectedCellPanel(myIpnbPanels.get(index + 1));
    }
    else if (index > 0) {
      setSelectedCellPanel(myIpnbPanels.get(index - 1));
    }
    else {
      mySelectedCellPanel = null;
      repaint();
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (mySelectedCellPanel != null) {
      g.setColor(mySelectedCellPanel.isEditing() ? JBColor.GREEN : JBColor.GRAY);
      g.drawRoundRect(mySelectedCellPanel.getX() - 50, mySelectedCellPanel.getTop() - 1,
                      mySelectedCellPanel.getWidth() + 145 - IpnbEditorUtil.PROMPT_SIZE.width, mySelectedCellPanel.getHeight() + 2, 5, 5);
    }
  }

  private void updateCellSelection(MouseEvent e) {
    if (e.getClickCount() > 0) {
      IpnbEditablePanel ipnbPanel = getIpnbPanelByClick(e.getPoint());
      if (ipnbPanel != null) {
        ipnbPanel.setEditing(false);
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          IdeFocusManager.getGlobalInstance().requestFocus(ipnbPanel, true);
        });
        repaint();
        setSelectedCell(ipnbPanel, true);
      }
    }
  }

  public void setInitialPosition(int index) {
    myInitialSelection = index;
  }

  public void setSelectedCellPanel(@NotNull final IpnbEditablePanel ipnbPanel) {
    setSelectedCell(ipnbPanel, false);
  }

  public void setSelectedCell(@NotNull final IpnbEditablePanel ipnbPanel, boolean mouse) {
    if (ipnbPanel.equals(mySelectedCellPanel)) return;
    if (mySelectedCellPanel != null) {
      mySelectedCellPanel.setEditing(false);
    }
    mySelectedCellPanel = ipnbPanel;
    revalidateAndRepaint();
    if (ipnbPanel.getBounds().getHeight() != 0) {
      myListener.selectionChanged(ipnbPanel, mouse);
    }
  }

  public void revalidateAndRepaint() {
    revalidate();
    UIUtil.requestFocus(this);
    repaint();
  }

  @Nullable
  public IpnbEditablePanel getSelectedCellPanel() {
    return mySelectedCellPanel;
  }

  public int getSelectedIndex() {
    final IpnbEditablePanel selectedCellPanel = getSelectedCellPanel();
    return myIpnbPanels.indexOf(selectedCellPanel);
  }

  @Nullable
  private IpnbEditablePanel getIpnbPanelByClick(@NotNull final Point point) {
    for (IpnbEditablePanel c : myIpnbPanels) {
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
    final IpnbEditablePanel selectedCellPanel = getSelectedCellPanel();
    if (OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
      if (selectedCellPanel instanceof IpnbCodePanel) {
        return ((IpnbCodePanel)selectedCellPanel).getEditor();
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
