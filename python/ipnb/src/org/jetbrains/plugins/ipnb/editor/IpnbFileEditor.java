// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.editor;

import com.google.common.collect.Lists;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLoadingPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.actions.*;
import org.jetbrains.plugins.ipnb.editor.panels.*;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.IpnbParser;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbHeadingCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;

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

  private final JBLoadingPanel myEditorPanel;
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
  private final JScrollPane myScrollPane;
  public static final DataKey<IpnbFileEditor> DATA_KEY = DataKey.create(IpnbFileEditor.class.getName());
  private JComponent myToolbar;

  public IpnbFileEditor(Project project, final VirtualFile vFile) {
    myDocument = FileDocumentManager.getInstance().getDocument(vFile);
    project.getMessageBus().connect(this)
      .subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, new FileEditorManagerListener.Before() {
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

    myEditorPanel = new JBLoadingPanel(new BorderLayout(), this);
    myEditorPanel.startLoading();
    myEditorPanel.setBackground(IpnbEditorUtil.getBackground());

    myIpnbFilePanel = createIpnbEditorPanel(project, vFile);
    final JPanel controlPanel = createControlPanel();
    myEditorPanel.add(controlPanel, BorderLayout.NORTH);
    myScrollPane = ScrollPaneFactory.createScrollPane(myIpnbFilePanel);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    myEditorPanel.add(myScrollPane, BorderLayout.CENTER);
    registerHeadingActions();
    registerActions();
  }

  public void loaded() {
    myEditorPanel.stopLoading();
  }

  public Document getDocument() {
    return myDocument;
  }

  private void registerActions() {
    new IpnbAddCellAboveAction(this).registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("ctrl shift EQUALS")),
                                                               myIpnbFilePanel);
    new IpnbMarkdownCellAction(this).registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("ctrl shift M")),
                                                               myIpnbFilePanel);
    new IpnbCodeCellAction(this).registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("ctrl shift Y")),
                                                           myIpnbFilePanel);

    new IpnbMoveCellDownAction(this);
    new IpnbMoveCellUpAction(this);
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
    myCellTypeCombo = new ComboBox(ourCellTypes);

    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.addAll(new IpnbRunCellAction(this), new IpnbInterruptKernelAction(this), new IpnbReloadKernelAction(this));
    toolbarGroup.add(new Separator());
    toolbarGroup.addAll(new IpnbMoveCellUpAction(this), new IpnbMoveCellDownAction(this));
    toolbarGroup.add(new IpnbAddCellBelowAction(this));

    myToolbar = createToolbar(toolbarGroup);
    controlPanel.add(myToolbar);

    myCellTypeCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final Object selectedItem = myCellTypeCombo.getSelectedItem();
        final IpnbEditablePanel selectedCellPanel = myIpnbFilePanel.getSelectedCellPanel();
        if (selectedCellPanel != null && selectedItem instanceof String) {
          updateCellType((String)selectedItem, selectedCellPanel);
        }
      }
    });
    controlPanel.add(myCellTypeCombo);

    final IpnbPanel selectedCellPanel = myIpnbFilePanel.getSelectedCellPanel();
    if (selectedCellPanel != null) {
      updateCellTypeCombo(selectedCellPanel);
    }

    final MatteBorder border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.GRAY);
    controlPanel.setBorder(border);
    return controlPanel;
  }

  @NotNull
  private static JComponent createToolbar(@NotNull DefaultActionGroup group) {
    JComponent component = ActionManager.getInstance().createActionToolbar("IpnbEditor", group, true).getComponent();
    component.setBackground(IpnbEditorUtil.getBackground());

    return component;
  }

  public RelativePoint getRunButtonPlace() {
    return RelativePoint.getNorthWestOf(myToolbar);
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
        final IpnbMarkdownCell markdownCell = new IpnbMarkdownCell(cell.getSource(), cell.getMetadata());
        if (index >= 0) {
          cells.set(index, markdownCell);
        }
        myIpnbFilePanel.replaceComponent(selectedCell, markdownCell);
      }
      else if (selectedItem.equals(codeCellType)) {
        final List<IpnbCell> cells = myIpnbFilePanel.getIpnbFile().getCells();
        final int index = cells.indexOf(((IpnbHeadingPanel)selectedCell).getCell());
        final IpnbCodeCell codeCell = new IpnbCodeCell("python", cell.getSource(), null, Lists.newArrayList(),
                                                       cell.getMetadata());
        if (index >= 0) {
          cells.set(index, codeCell);
        }

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
        final IpnbHeadingCell headingCell = new IpnbHeadingCell(cell.getSource(), level, cell.getMetadata());
        if (index >= 0) {
          cells.set(index, headingCell);
        }

        myIpnbFilePanel.replaceComponent(selectedCell, headingCell);
      }
      else if (selectedItem.equals(codeCellType)) {
        final List<IpnbCell> cells = myIpnbFilePanel.getIpnbFile().getCells();
        final int index = cells.indexOf(((IpnbMarkdownPanel)selectedCell).getCell());
        final IpnbCodeCell codeCell = new IpnbCodeCell("python", cell.getSource(), null, Lists.newArrayList(), cell.getMetadata());
        if (index >= 0) {
          cells.set(index, codeCell);
        }

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
        final IpnbHeadingCell headingCell = new IpnbHeadingCell(cell.getSource(), level, cell.getMetadata());
        if (index >= 0) {
          cells.set(index, headingCell);
        }
        myIpnbFilePanel.replaceComponent(selectedCell, headingCell);
      }
      else if (selectedItem.equals(markdownCellType)) {
        final List<IpnbCell> cells = myIpnbFilePanel.getIpnbFile().getCells();
        final int index = cells.indexOf(((IpnbCodePanel)selectedCell).getCell());
        final IpnbMarkdownCell markdownCell = new IpnbMarkdownCell(cell.getSource(), cell.getMetadata());
        if (index >= 0) {
          cells.set(index, markdownCell);
        }

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
                                 if (myCellTypeCombo == null) return;
                                 updateCellTypeCombo(ipnbPanel);
                                 if (byMouse) return;
                                 updateScrollPosition(ipnbPanel);
                               }
                             });
  }

  public void updateScrollPosition(@NotNull final IpnbPanel ipnbPanel) {
    final Rectangle rect = myIpnbFilePanel.getVisibleRect();

    final Rectangle cellBounds = ipnbPanel.getBounds();
    final int shift = 2;
    if (cellBounds.getY() <= rect.getY() || cellBounds.getY() >= rect.getY() + rect.height) {
      myScrollPane.getVerticalScrollBar().setValue(cellBounds.y - shift);
    }
    if (cellBounds.getY() + cellBounds.getHeight() > rect.getY() + rect.getHeight() && cellBounds.height < rect.height) {
      myScrollPane.getVerticalScrollBar().setValue(cellBounds.y - rect.height + cellBounds.height + shift);
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
    Disposer.dispose(myIpnbFilePanel);
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
