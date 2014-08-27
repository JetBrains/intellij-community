package org.jetbrains.plugins.ipnb.editor;

import com.google.common.collect.Lists;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbAddCellAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbRunCellAction;
import org.jetbrains.plugins.ipnb.editor.actions.IpnbSaveAction;
import org.jetbrains.plugins.ipnb.editor.panels.*;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.format.IpnbParser;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCodeCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbHeadingCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;
import org.jetbrains.plugins.ipnb.format.cells.IpnbMarkdownCell;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.List;

/**
 * @author traff
 */
public class IpnbFileEditor extends UserDataHolderBase implements FileEditor, TextEditor {
  private final Project myProject;
  private final VirtualFile myFile;

  private final String myName;

  private final JComponent myEditorPanel;

  private final TextEditor myEditor;
  private final IpnbFilePanel myIpnbFilePanel;
  private ComboBox myCellTypeCombo;
  private static final String codeCellType = "Code";
  private static final String markdownCellType = "Markdown";
  private static final String headingCellType = "Heading ";
  private static final String rawNBCellType = "Raw NBConvert";
  private final static String[] ourCellTypes = new String[]{codeCellType, markdownCellType, /*rawNBCellType, */headingCellType + "1",
    headingCellType + "2", headingCellType + "3", headingCellType + "4", headingCellType + "5", headingCellType + "6"};
  private JButton myRunCellButton;


  public IpnbFileEditor(Project project, VirtualFile vFile) {
    myProject = project;

    myFile = vFile;

    myName = vFile.getName();

    myEditor = createEditor(project, vFile);

    myEditorPanel = new JPanel(new BorderLayout());
    myEditorPanel.setBackground(IpnbEditorUtil.getBackground());

    myIpnbFilePanel = createIpnbEditorPanel(myProject, vFile, this);

    final JPanel controlPanel = createControlPanel();
    myEditorPanel.add(controlPanel, BorderLayout.NORTH);
    myEditorPanel.add(new MyScrollPane(myIpnbFilePanel), BorderLayout.CENTER);
  }

  private JPanel createControlPanel() {
    final JPanel controlPanel = new JPanel();
    controlPanel.setBackground(IpnbEditorUtil.getBackground());
    addSaveButton(controlPanel);
    addAddButton(controlPanel);

    addRunButton(controlPanel);

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
    updateCellTypeCombo(selectedCell);
    controlPanel.add(myCellTypeCombo);
    final MatteBorder border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.GRAY);
    controlPanel.setBorder(border);
    return controlPanel;
  }

  private void addRunButton(@NotNull final JPanel controlPanel) {
    myRunCellButton = new JButton();
    myRunCellButton.setBackground(IpnbEditorUtil.getBackground());
    myRunCellButton.setPreferredSize(new Dimension(30, 30));
    myRunCellButton.setIcon(AllIcons.General.Run);
    myRunCellButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final IpnbRunCellAction action = (IpnbRunCellAction)ActionManager.getInstance().getAction("IpnbRunCellAction");
        action.runCell(myIpnbFilePanel);
      }
    });
    controlPanel.add(myRunCellButton);
  }

  private void addSaveButton(@NotNull final JPanel controlPanel) {
    final JButton saveButton = new JButton();
    saveButton.setBackground(IpnbEditorUtil.getBackground());
    saveButton.setPreferredSize(new Dimension(30, 30));
    saveButton.setIcon(AllIcons.Actions.Menu_saveall);
    saveButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final IpnbSaveAction action = (IpnbSaveAction)ActionManager.getInstance().getAction("IpnbSaveAction");
        action.saveAndCheckpoint(myIpnbFilePanel.getIpnbFile());
      }
    });
    controlPanel.add(saveButton);
  }

  private void addAddButton(@NotNull final JPanel controlPanel) {
    final JButton saveButton = new JButton();
    saveButton.setBackground(IpnbEditorUtil.getBackground());
    saveButton.setPreferredSize(new Dimension(30, 30));
    saveButton.setIcon(AllIcons.General.Add);
    saveButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final IpnbAddCellAction action = (IpnbAddCellAction)ActionManager.getInstance().getAction("IpnbAddCellAction");
        action.addCell(myIpnbFilePanel);
      }
    });
    controlPanel.add(saveButton);
  }

  public JButton getRunCellButton() {
    return myRunCellButton;
  }

  private void updateCellType(@NotNull final String selectedItem, @NotNull final IpnbEditablePanel selectedCell) {
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
  private IpnbFilePanel createIpnbEditorPanel(Project project, VirtualFile vFile, Disposable parent) {
    try {
      return new IpnbFilePanel(project, parent, IpnbParser.parseIpnbFile(vFile),
                               new CellSelectionListener() {
                                 @Override
                                 public void selectionChanged(@NotNull IpnbPanel ipnbPanel) {
                                   if (myCellTypeCombo == null) return;
                                   updateCellTypeCombo(ipnbPanel);
                                 }
                               });
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, e.getMessage(), "Can't open " + vFile.getPath());
      throw new IllegalStateException(e);
    }
  }

  private void updateCellTypeCombo(IpnbPanel ipnbPanel) {
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
    return new IpnbEditorState(-1, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
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
    Disposer.dispose(myEditor);
  }

  @NotNull
  @Override
  public Editor getEditor() {
    return myEditor.getEditor();
  }

  @Override
  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    return true;
  }

  @Override
  public void navigateTo(@NotNull Navigatable navigatable) {
  }

  @Nullable
  private static TextEditor createEditor(@NotNull Project project, @NotNull VirtualFile vFile) {
    FileEditorProvider provider = getProvider(project, vFile);

    if (provider != null) {
      FileEditor editor = provider.createEditor(project, vFile);
      if (editor instanceof TextEditor) {
        return (TextEditor)editor;
      }
    }
    return null;
  }

  @Nullable
  private static FileEditorProvider getProvider(Project project, VirtualFile vFile) {
    FileEditorProvider[] providers = FileEditorProviderManagerImpl.getInstance().getProviders(project, vFile);
    for (FileEditorProvider provider : providers) {
      if (!(provider instanceof IpnbEditorProvider)) {
        return provider;
      }
    }
    return null;
  }

  private class MyScrollPane extends JBScrollPane {
    private MyScrollPane(Component view) {
      super(view);
    }

    @Override
    public JScrollBar createVerticalScrollBar() {
      return new MyScrollBar(this);
    }
  }

  private class MyScrollBar extends JBScrollBar {
    private MyScrollPane myScrollPane;

    public MyScrollBar(MyScrollPane scrollPane) {
      myScrollPane = scrollPane;
    }

    @Override
    public int getUnitIncrement(int direction) {
      return myEditor.getEditor().getLineHeight();
    }

    @Override
    public int getBlockIncrement(int direction) {
      return myEditor.getEditor().getLineHeight();
    }
  }

  public abstract class CellSelectionListener {
    public abstract void selectionChanged(@NotNull final IpnbPanel ipnbPanel);
  }
}
