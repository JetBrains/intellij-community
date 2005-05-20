package com.intellij.debugger.ui.breakpoints;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.Table;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.actionSystem.*;
import com.intellij.psi.PsiField;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * @author Jeka
 */
public class BreakpointPanel {
  private final BreakpointTableModel myTableModel;
  private final BreakpointPropertiesPanel myPropertiesPanel;
  private final BreakpointPanelAction[] myActions;
  private Breakpoint myCurrentViewableBreakpoint;

  private JPanel myPanel;
  private JPanel myBreakPointsPanel;
  private JPanel myTablePlace;
  private JPanel myPropertiesPanelPlace;
  private Table myTable;
  private JPanel myButtonsPanel;


  public static abstract class BreakpointPanelAction implements ActionListener {
    private final String myName;
    private JButton myButton;
    private BreakpointPanel myPanel;

    protected BreakpointPanelAction(String name) {
      myName = name;
    }

    public final String getName() {
      return myName;
    }
    
    protected void setPanel(BreakpointPanel panel) {
      myPanel = panel;
    }

    protected final BreakpointPanel getPanel() {
      return myPanel;
    }

    protected void setButton(JButton button) {
      myButton = button;
    }

    protected final JButton getButton() {
      return myButton;
    }

    protected abstract void update();
  }
  
  public static abstract class AddAction extends BreakpointPanelAction {
    protected AddAction() {
      super("Add...");
    }

    protected void setButton(JButton button) {
      super.setButton(button);
      getButton().setMnemonic('A');
    }

    protected void setPanel(BreakpointPanel panel) {
      super.setPanel(panel);
      getPanel().getTable().registerKeyboardAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    protected void update() {
    }
  }
  
  public static class RemoveAction extends BreakpointPanelAction {
    private final Project myProject;

    protected RemoveAction(final Project project) {
      super("Remove...");
      myProject = project;
    }

    protected void setButton(JButton button) {
      super.setButton(button);
      getButton().setMnemonic('R');
    }

    protected void setPanel(BreakpointPanel panel) {
      super.setPanel(panel);
      getPanel().getTable().registerKeyboardAction(
        this, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );
    }

    public void actionPerformed(ActionEvent e) {
      Breakpoint[] breakpoints = getPanel().getSelectedBreakpoints();
      if (breakpoints != null) {
        for (int idx = 0; idx < breakpoints.length; idx++) {
          if (breakpoints[idx] instanceof AnyExceptionBreakpoint) {
            return;
          }
        }
        getPanel().removeSelectedBreakpoints();
        BreakpointManager manager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
        for (int idx = 0; idx < breakpoints.length; idx++) {
          manager.removeBreakpoint(breakpoints[idx]);
        }
      }
      getPanel().getTable().requestFocus();
    }

    protected void update() {
      getButton().setEnabled(getPanel().getSelectedBreakpoints().length > 0);
    }
  }

  public static class GotoSourceAction extends BreakpointPanelAction {
    private final Project myProject;

    protected GotoSourceAction(final Project project) {
      super("Go to");
      myProject = project;
    }

    public void actionPerformed(ActionEvent e) {
      gotoSource();
    }
    private void gotoSource() {
      OpenFileDescriptor editSourceDescriptor = getPanel().createEditSourceDescriptor(myProject);
      if (editSourceDescriptor != null) {
        FileEditorManager.getInstance(myProject).openTextEditor(editSourceDescriptor, true);
      }
    }
    protected void setButton(JButton button) {
      super.setButton(button);
      getButton().setMnemonic('G');
    }

    protected void setPanel(BreakpointPanel panel) {
      super.setPanel(panel);
      ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet();
      new AnAction() {
        public void actionPerformed(AnActionEvent e){
          gotoSource();
        }
      }.registerCustomShortcutSet(shortcutSet, getPanel().getPanel());
    }

    protected void update() {
      getButton().setEnabled(getPanel().getCurrentViewableBreakpoint() != null);
    }
  }

  public static class ViewSourceAction extends BreakpointPanelAction {
    private final Project myProject;

    protected ViewSourceAction(final Project project) {
      super("View Source");
      myProject = project;
    }

    protected void setButton(JButton button) {
      super.setButton(button);
      getButton().setMnemonic('S');
    }

    public void actionPerformed(ActionEvent e) {
      OpenFileDescriptor editSourceDescriptor = getPanel().createEditSourceDescriptor(myProject);
      if (editSourceDescriptor != null) {
        FileEditorManager.getInstance(myProject).openTextEditor(editSourceDescriptor, false);
      }
    }

    protected void update() {
      getButton().setEnabled(getPanel().getCurrentViewableBreakpoint() != null);
    }
  }
  
  public BreakpointPanel(BreakpointTableModel tableModel, BreakpointPropertiesPanel propertiesPanel, final BreakpointPanelAction[] actions) {
    myTableModel = tableModel;
    myPropertiesPanel = propertiesPanel;
    myActions = actions;
    myTable = new Table(myTableModel);
    myTable.setColumnSelectionAllowed(false);
    InputMap inputMap = myTable.getInputMap();
    ActionMap actionMap = myTable.getActionMap();
    Object o = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
    if (o == null) {
      o = "enable_disable";
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), o);
    }
    actionMap.put(o, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) return;
        int[] indices = myTable.getSelectedRows();
        boolean currentlyMarked = true;
        for (int i = 0; i < indices.length; i++) {
          final Boolean isMarked = (Boolean)myTable.getValueAt(indices[i], BreakpointTableModel.ENABLED_STATE);
          currentlyMarked = isMarked != null? isMarked.booleanValue() : false;
          if (!currentlyMarked) {
            break;
          }
        }
        final Boolean valueToSet = currentlyMarked ? Boolean.FALSE : Boolean.TRUE;
        for (int i = 0; i < indices.length; i++) {
          myTable.setValueAt(valueToSet, indices[i], BreakpointTableModel.ENABLED_STATE);
        }
      }
    });

    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setTableHeader(null);
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTable);
    int width = new JCheckBox().getPreferredSize().width;
    TableColumnModel columnModel = myTable.getColumnModel();

    TableColumn enabledStateColumn = columnModel.getColumn(BreakpointTableModel.ENABLED_STATE);
    enabledStateColumn.setPreferredWidth(width);
    enabledStateColumn.setMaxWidth(width);
    final Class enabledStateColumnClass = myTableModel.getColumnClass(BreakpointTableModel.ENABLED_STATE);
    final TableCellRenderer delegateRenderer = myTable.getDefaultRenderer(enabledStateColumnClass);
    myTable.setDefaultRenderer(enabledStateColumnClass, new DefaultTableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        final Component component = delegateRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (component instanceof JComponent) {
          ((JComponent)component).setBorder(null);
        }
        return component;
      }
    });
    columnModel.getColumn(BreakpointTableModel.NAME).setCellRenderer(new BreakpointNameCellRenderer());

    myTablePlace.setLayout(new BorderLayout());
    myTablePlace.add(pane, BorderLayout.CENTER);

    addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        refreshUI();
      }
    });
    myTableModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        if (e.getType() == TableModelEvent.UPDATE) {
          refreshUI();
        }
      }
    });
    myTable.requestFocus();

    myStubPanel = new JPanel();
    myStubPanel.setMinimumSize(myPropertiesPanel.getPanel().getMinimumSize());

    myPropertiesPanelPlace.setLayout(new BorderLayout());
    myBreakPointsPanel.setBorder(IdeBorderFactory.createEmptyBorder(6, 6, 0, 6));
    
    myButtonsPanel.setLayout(new GridBagLayout());
    for (int idx = 0; idx < actions.length; idx++) {
      final BreakpointPanelAction action = actions[idx];
      final JButton button = new JButton(action.getName());
      button.addActionListener(action);
      action.setButton(button);
      action.setPanel(this);
      final double weighty = (idx == actions.length - 1) ? 1.0 : 0.0;
      myButtonsPanel.add(button, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, weighty, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 2, 2, 2), 0, 0));
    }
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });
    refreshUI();
  }

  public Breakpoint getCurrentViewableBreakpoint() {
    return myCurrentViewableBreakpoint;
  }

  public void saveChanges() {
    if (myCurrentViewableBreakpoint != null) {
      myPropertiesPanel.saveTo(myCurrentViewableBreakpoint, new Runnable() {
        public void run() {
          myTable.repaint();
        }
      });
    }
  }

  public void updateButtons() {
    for (int idx = 0; idx < myActions.length; idx++) {
      final BreakpointPanelAction action = myActions[idx];
      final JButton button = action.getButton();
      action.update();
      if (!button.isEnabled() && button.hasFocus()) {
        button.transferFocus();
      }
    }
  }

  public JTable getTable() {
    return myTable;
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void selectBreakpoint(Breakpoint breakpoint) {
    int index = myTableModel.getBreakpointIndex(breakpoint);
    myTable.clearSelection();
    myTable.getSelectionModel().addSelectionInterval(index, index);
    myPropertiesPanel.getControl(BreakpointPropertiesPanel.CONTROL_LOG_MESSAGE);
  }

  public void setBreakpoints(Breakpoint[] breakpoints) {
    myTableModel.setBreakpoints(breakpoints);
    if (breakpoints != null && breakpoints.length > 0) {
      myTable.getSelectionModel().addSelectionInterval(0, 0);
    }
  }

  public Breakpoint[] getSelectedBreakpoints() {
    if (myTable.getRowCount() == 0) {
      return new Breakpoint[0];
    }

    int[] rows = myTable.getSelectedRows();
    if (rows.length == 0) {
      return new Breakpoint[0];
    }
    Breakpoint[] rv = new Breakpoint[rows.length];
    for (int idx = 0; idx < rows.length; idx++) {
      rv[idx] = myTableModel.getBreakpoint(rows[idx]);
    }
    return rv;
  }

  public void removeSelectedBreakpoints() {
    TableUtil.removeSelectedItems(myTable);
    myCurrentViewableBreakpoint = null;
    refreshUI();
  }

  public void insertBreakpointAt(Breakpoint breakpoint, int index) {
    myTableModel.insertBreakpointAt(breakpoint, index);
    ListSelectionModel model = myTable.getSelectionModel();
    model.clearSelection();
    model.addSelectionInterval(index, index);
  }

  public void addBreakpoint(Breakpoint breakpoint) {
    myTableModel.addBreakpoint(breakpoint);
    int index = myTable.getRowCount() - 1;
    ListSelectionModel model = myTable.getSelectionModel();
    model.clearSelection();
    model.addSelectionInterval(index, index);
  }

  public void addListSelectionListener(ListSelectionListener listener) {
    myTable.getSelectionModel().addListSelectionListener(listener);
  }

  public void removeListSelectionListener(ListSelectionListener listener) {
    myTable.getSelectionModel().removeListSelectionListener(listener);
  }

  private JPanel myStubPanel;

  private void refreshUI() {
    if (myCurrentViewableBreakpoint != null) {
      myPropertiesPanel.saveTo(myCurrentViewableBreakpoint, new Runnable() {
        public void run() {
          myTable.repaint();
        }
      });
    }
    Breakpoint[] breakpoints = getSelectedBreakpoints();
    Breakpoint oldBreakpoint = myCurrentViewableBreakpoint;
    myCurrentViewableBreakpoint = (breakpoints != null && breakpoints.length == 1) ? breakpoints[0] : null;
    if (myCurrentViewableBreakpoint != null) {
      if (oldBreakpoint == null) {
        myPropertiesPanelPlace.remove(myStubPanel);
        myPropertiesPanelPlace.add(myPropertiesPanel.getPanel());
        myPropertiesPanelPlace.repaint();
      }
      myPropertiesPanel.initFrom(myCurrentViewableBreakpoint);
    }
    else {
      myPropertiesPanelPlace.remove(myPropertiesPanel.getPanel());
      myPropertiesPanelPlace.add(myStubPanel);
      myPropertiesPanelPlace.repaint();
    }
    updateButtons();
  }

  public JComponent getControl(String control) {
    return myPropertiesPanel.getControl(control);
  }

  public int getBreakpointCount() {
    return myTable.getRowCount();
  }

  public Breakpoint getBreakpointAt(final int idx) {
    return ((BreakpointTableModel)myTable.getModel()).getBreakpoint(idx);
  }

  public boolean isBreakpointEnabled(final int idx) {
    return ((BreakpointTableModel)myTable.getModel()).isBreakpointEnabled(idx);
  }

  public void dispose() {
    myPropertiesPanel.dispose();
  }

  private OpenFileDescriptor createEditSourceDescriptor(final Project project) {
    Breakpoint[] breakpoints = this.getSelectedBreakpoints();
    if (breakpoints == null || breakpoints.length == 0) return null;
    Breakpoint br = breakpoints[0];
    int line;
    Document doc;
    if (br instanceof BreakpointWithHighlighter) {
      BreakpointWithHighlighter breakpoint = (BreakpointWithHighlighter)br;
      doc = breakpoint.getDocument();
      line = breakpoint.getLineIndex();
    }
    else {
      return null;
    }
    if (line < 0 || line >= doc.getLineCount()) return null;
    int offset = doc.getLineStartOffset(line);
    if(br instanceof FieldBreakpoint) {
      PsiField field = ((FieldBreakpoint) br).getPsiField();
      if(field != null) {
        offset = field.getTextOffset();
      }
    }
    VirtualFile vFile = FileDocumentManager.getInstance().getFile(doc);
    return new OpenFileDescriptor(project, vFile, offset);
  }
}