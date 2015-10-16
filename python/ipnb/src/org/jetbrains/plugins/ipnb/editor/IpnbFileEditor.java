package org.jetbrains.plugins.ipnb.editor;

import com.google.common.collect.Lists;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.actions.*;
import org.jetbrains.plugins.ipnb.editor.panels.*;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.IpnbParser;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbHeadingCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;

/**
 * @author traff
 */
public class IpnbFileEditor extends UserDataHolderBase implements FileEditor {
  private final VirtualFile myFile;

  private final String myName;

  private final JComponent myEditorPanel;

  private final IpnbFilePanel myIpnbFilePanel;
  private final Document myDocument;
  private ComboBox myCellTypeCombo;
  private static final String codeCellType = "Code";
  private static final String markdownCellType = "Markdown";
  private static final String headingCellType = "Heading ";
  @SuppressWarnings("UnusedDeclaration")
  private static final String rawNBCellType = "Raw NBConvert";
  private final static String[] ourCellTypes = new String[]{codeCellType, markdownCellType, /*rawNBCellType, */headingCellType + "1",
    headingCellType + "2", headingCellType + "3", headingCellType + "4", headingCellType + "5", headingCellType + "6"};
  private JButton myRunCellButton;
  private final JScrollPane myScrollPane;


  public IpnbFileEditor(Project project, final VirtualFile vFile) {
    myDocument = FileDocumentManager.getInstance().getDocument(vFile);
    project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, new FileEditorManagerListener.Before.Adapter() {
      @Override
      public void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (!new File(file.getPath()).exists()) return;

        if (myDocument == null) return;
        FileDocumentManager.getInstance().saveDocument(myDocument);
        IpnbParser.saveIpnbFile(myIpnbFilePanel);
        file.refresh(false, false);
      }
    });

    myFile = vFile;

    myName = vFile.getName();

    myEditorPanel = new JPanel(new BorderLayout());
    myEditorPanel.setBackground(IpnbEditorUtil.getBackground());

    myIpnbFilePanel = createIpnbEditorPanel(project, vFile);
    Disposer.register(this, myIpnbFilePanel);
    final JPanel controlPanel = createControlPanel();
    myEditorPanel.add(controlPanel, BorderLayout.NORTH);
    myScrollPane = ScrollPaneFactory.createScrollPane(myIpnbFilePanel);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    myEditorPanel.add(myScrollPane, BorderLayout.CENTER);
    registerHeadingActions();
  }

  public Document getDocument() {
    return myDocument;
  }

  private void registerHeadingActions() {
    new IpnbHeading1CellAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("ctrl shift 1")), myIpnbFilePanel);
    new IpnbHeading2CellAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("ctrl shift 2")), myIpnbFilePanel);
    new IpnbHeading3CellAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("ctrl shift 3")), myIpnbFilePanel);
    new IpnbHeading4CellAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("ctrl shift 4")), myIpnbFilePanel);
    new IpnbHeading5CellAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("ctrl shift 5")), myIpnbFilePanel);
    new IpnbHeading6CellAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("ctrl shift 6")), myIpnbFilePanel);
  }

  private JPanel createControlPanel() {
    final JPanel controlPanel = new JPanel();
    controlPanel.setBackground(IpnbEditorUtil.getBackground());

    final JPanel savePanel = new JPanel(new GridBagLayout());
    savePanel.setBackground(IpnbEditorUtil.getBackground());
    addSaveButton(savePanel);
    controlPanel.add(savePanel);

    final JPanel addPanel = new JPanel(new GridBagLayout());
    addPanel.setBackground(IpnbEditorUtil.getBackground());
    addAddButton(addPanel);
    controlPanel.add(addPanel);

    final JPanel editorPanel = new JPanel(new GridBagLayout());
    editorPanel.setBackground(IpnbEditorUtil.getBackground());
    addCutButton(editorPanel);
    addCopyButton(editorPanel);
    addPasteButton(editorPanel);
    controlPanel.add(editorPanel);

    final JPanel runPanel = new JPanel(new GridBagLayout());
    runPanel.setBackground(IpnbEditorUtil.getBackground());
    addRunButton(runPanel);
    controlPanel.add(runPanel);

    addInterruptKernelButton(runPanel);
    addReloadKernelButton(runPanel);
    myCellTypeCombo = new ComboBox(ourCellTypes);

    myCellTypeCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final Object selectedItem = myCellTypeCombo.getSelectedItem();
        final IpnbEditablePanel selectedCell = myIpnbFilePanel.getSelectedCell();
        if (selectedCell != null && selectedItem instanceof String) {
          updateCellType((String)selectedItem, selectedCell);
        }
      }
    });
    final IpnbPanel selectedCell = myIpnbFilePanel.getSelectedCell();
    if (selectedCell != null) {
      updateCellTypeCombo(selectedCell);
    }
    controlPanel.add(myCellTypeCombo);
    final MatteBorder border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.GRAY);
    controlPanel.setBorder(border);
    return controlPanel;
  }

  private void addRunButton(@NotNull final JPanel controlPanel) {
    myRunCellButton = new JButton();
    myRunCellButton.setBackground(IpnbEditorUtil.getBackground());
    myRunCellButton.setPreferredSize(new Dimension(30, 30));
    myRunCellButton.setIcon(AllIcons.Toolwindows.ToolWindowRun);
    myRunCellButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IpnbRunCellBaseAction.runCell(myIpnbFilePanel, true);
      }
    });
    myRunCellButton.setToolTipText("Run Cell");
    controlPanel.add(myRunCellButton);
  }

  private void addInterruptKernelButton(@NotNull final JPanel controlPanel) {
    addButton(controlPanel, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IpnbInterruptKernelAction.interruptKernel(IpnbFileEditor.this);
      }
    }, AllIcons.Actions.Suspend, "Interrupt kernel");
  }

  private void addReloadKernelButton(@NotNull final JPanel controlPanel) {
    addButton(controlPanel, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IpnbReloadKernelAction.reloadKernel(IpnbFileEditor.this);
      }
    }, AllIcons.Actions.Refresh, "Restart kernel");
  }

  private void addSaveButton(@NotNull final JPanel controlPanel) {
    addButton(controlPanel, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IpnbSaveAction.saveAndCheckpoint(IpnbFileEditor.this);
      }
    }, AllIcons.Actions.Menu_saveall, "Save and Checkpoint");
  }

  private void addCutButton(@NotNull final JPanel controlPanel) {
    addButton(controlPanel, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IpnbCutCellAction.cutCell(myIpnbFilePanel);
      }
    }, AllIcons.Actions.Menu_cut, "Cut Cell");
  }

  private void addCopyButton(@NotNull final JPanel controlPanel) {
    addButton(controlPanel, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IpnbCopyCellAction.copyCell(myIpnbFilePanel);
      }
    }, AllIcons.Actions.Copy, "Copy Cell");
  }

  private void addPasteButton(@NotNull final JPanel controlPanel) {
    addButton(controlPanel, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IpnbPasteCellAction.pasteCell(myIpnbFilePanel);
      }
    }, AllIcons.Actions.Menu_paste, "Paste Cell Below");
  }

  private static void addButton(@NotNull final JPanel controlPanel,
                                @NotNull final ActionListener listener,
                                @NotNull final Icon icon,
                                @NotNull final String tooltip) {
    final JButton button = new JButton();
    button.setBackground(IpnbEditorUtil.getBackground());
    button.setPreferredSize(new Dimension(30, 30));
    button.setIcon(icon);
    button.addActionListener(listener);
    button.setToolTipText(tooltip);
    controlPanel.add(button);
  }

  private void addAddButton(@NotNull final JPanel controlPanel) {
    addButton(controlPanel, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IpnbAddCellBelowAction.addCell(myIpnbFilePanel);
      }
    }, AllIcons.General.Add, "Insert Cell Below");
  }

  public JButton getRunCellButton() {
    return myRunCellButton;
  }

  private void updateCellType(@NotNull final String selectedItem, @NotNull final IpnbEditablePanel selectedCell) {
    selectedCell.updateCellSource();
    if (selectedCell instanceof IpnbHeadingPanel) {
      final IpnbHeadingCell cell = ((IpnbHeadingPanel)selectedCell).getCell();
      if (selectedItem.startsWith(headingCellType)) {
        final char c = selectedItem.charAt(selectedItem.length() - 1);
        final int level = Character.getNumericValue(c);
        if (level != cell.getLevel()) {
          cell.setLevel(level);
          selectedCell.updateCellView();
        }
      }
      else if (selectedItem.equals(markdownCellType)) {
        final List<IpnbCell> cells = myIpnbFilePanel.getIpnbFile().getCells();
        final int index = cells.indexOf(((IpnbHeadingPanel)selectedCell).getCell());
        final IpnbMarkdownCell markdownCell = new IpnbMarkdownCell(cell.getSource());
        if (index >= 0)
          cells.set(index, markdownCell);
        myIpnbFilePanel.replaceComponent(selectedCell, markdownCell);
      }
      else if (selectedItem.equals(codeCellType)) {
        final List<IpnbCell> cells = myIpnbFilePanel.getIpnbFile().getCells();
        final int index = cells.indexOf(((IpnbHeadingPanel)selectedCell).getCell());
        final IpnbCodeCell codeCell = new IpnbCodeCell("python", cell.getSource(), null, Lists.<IpnbOutputCell>newArrayList());
        if (index >= 0)
          cells.set(index, codeCell);

        myIpnbFilePanel.replaceComponent(selectedCell, codeCell);
      }
    }
    else if (selectedCell instanceof IpnbMarkdownPanel) {
      final IpnbMarkdownCell cell = ((IpnbMarkdownPanel)selectedCell).getCell();
      if (selectedItem.startsWith(headingCellType)) {
        final char c = selectedItem.charAt(selectedItem.length() - 1);
        final int level = Character.getNumericValue(c);
        final List<IpnbCell> cells = myIpnbFilePanel.getIpnbFile().getCells();
        final int index = cells.indexOf(((IpnbMarkdownPanel)selectedCell).getCell());
        final IpnbHeadingCell headingCell = new IpnbHeadingCell(cell.getSource(), level);
        if (index >= 0)
          cells.set(index, headingCell);

        myIpnbFilePanel.replaceComponent(selectedCell, headingCell);
      }
      else if (selectedItem.equals(codeCellType)) {
        final List<IpnbCell> cells = myIpnbFilePanel.getIpnbFile().getCells();
        final int index = cells.indexOf(((IpnbMarkdownPanel)selectedCell).getCell());
        final IpnbCodeCell codeCell = new IpnbCodeCell("python", cell.getSource(), null, Lists.<IpnbOutputCell>newArrayList());
        if (index >= 0)
          cells.set(index, codeCell);

        myIpnbFilePanel.replaceComponent(selectedCell, codeCell);
      }
    }
    else if (selectedCell instanceof IpnbCodePanel) {
      final IpnbCodeCell cell = ((IpnbCodePanel)selectedCell).getCell();
      if (selectedItem.startsWith(headingCellType)) {
        final char c = selectedItem.charAt(selectedItem.length() - 1);
        final int level = Character.getNumericValue(c);
        final List<IpnbCell> cells = myIpnbFilePanel.getIpnbFile().getCells();
        final int index = cells.indexOf(((IpnbCodePanel)selectedCell).getCell());
        final IpnbHeadingCell headingCell = new IpnbHeadingCell(cell.getSource(), level);
        if (index >= 0)
          cells.set(index, headingCell);
        myIpnbFilePanel.replaceComponent(selectedCell, headingCell);
      }
      else if(selectedItem.equals(markdownCellType)) {
        final List<IpnbCell> cells = myIpnbFilePanel.getIpnbFile().getCells();
        final int index = cells.indexOf(((IpnbCodePanel)selectedCell).getCell());
        final IpnbMarkdownCell markdownCell = new IpnbMarkdownCell(cell.getSource());
        if (index >= 0)
          cells.set(index, markdownCell);

        myIpnbFilePanel.replaceComponent(selectedCell, markdownCell);
      }
    }
  }

  @NotNull
  private IpnbFilePanel createIpnbEditorPanel(Project project, VirtualFile vFile) {
    return new IpnbFilePanel(project, this, vFile,
                             new CellSelectionListener() {
                               @Override
                               public void selectionChanged(@NotNull IpnbPanel ipnbPanel, boolean byMouse) {
                                 if (myCellTypeCombo == null || byMouse) return;
                                 updateCellTypeCombo(ipnbPanel);
                                 updateScrollPosition(ipnbPanel);
                               }
                             });
  }

  public void updateScrollPosition(@NotNull final IpnbPanel ipnbPanel) {
    final Rectangle rect = myIpnbFilePanel.getVisibleRect();

    final Rectangle cellBounds = ipnbPanel.getBounds();
    if (cellBounds.getY() <= rect.getY()) {
      myScrollPane.getVerticalScrollBar().setValue(cellBounds.y - rect.height + cellBounds.height);
    }
    if (cellBounds.getY() + cellBounds.getHeight() > rect.getY() + rect.getHeight()) {
      myScrollPane.getVerticalScrollBar().setValue(cellBounds.y);
    }
  }

  private void updateCellTypeCombo(@NotNull final IpnbPanel ipnbPanel) {
    if (ipnbPanel instanceof IpnbHeadingPanel) {
      final IpnbHeadingCell cell = ((IpnbHeadingPanel)ipnbPanel).getCell();
      final int level = cell.getLevel();
      myCellTypeCombo.setSelectedItem(headingCellType + level);
    }
    else if (ipnbPanel instanceof IpnbMarkdownPanel) {
      myCellTypeCombo.setSelectedItem(markdownCellType);
    }
    else if (ipnbPanel instanceof IpnbCodePanel) {
      myCellTypeCombo.setSelectedItem(codeCellType);
    }
  }

  public IpnbFilePanel getIpnbFilePanel() {
    return myIpnbFilePanel;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myEditorPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditorPanel;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    final int index = getIpnbFilePanel().getSelectedIndex();
    final Document document = FileDocumentManager.getInstance().getCachedDocument(myFile);
    long modificationStamp = document != null ? document.getModificationStamp() : myFile.getModificationStamp();
    return new IpnbEditorState(modificationStamp, index);
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    final int index = ((IpnbEditorState)state).getSelectedIndex();
    myIpnbFilePanel.setInitialPosition(index);
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void dispose() {
  }

  public abstract static class CellSelectionListener {
    public abstract void selectionChanged(@NotNull final IpnbPanel ipnbPanel, boolean mouse);
  }

  public VirtualFile getVirtualFile() {
    return myFile;
  }

  public JScrollPane getScrollPane() {
    return myScrollPane;
  }
}
