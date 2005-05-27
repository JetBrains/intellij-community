package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.ui.breakpoints.actions.BreakpointPanelAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.EventDispatcher;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.util.EventListener;

/**
 * @author Jeka
 */
public class BreakpointPanel {
  private final BreakpointPropertiesPanel myPropertiesPanel;
  private final BreakpointPanelAction[] myActions;
  private Breakpoint myCurrentViewableBreakpoint;

  private JPanel myPanel;
  private JPanel myBreakPointsPanel;
  private JPanel myTablePlace;
  private JPanel myPropertiesPanelPlace;
  private BreakpointTable myTable;
  private BreakpointTree myTree;
  private JPanel myButtonsPanel;
  private EventDispatcher<ChangesListener> myEventDispatcher = EventDispatcher.create(ChangesListener.class);
  private String myCurrentViewId = TABLE_VIEW;
  
  private static final String PROPERTIES_STUB = "STUB";
  private static final String PROPERTIES_DATA = "DATA";
  
  public static final String TREE_VIEW = "TREE";
  public static final String TABLE_VIEW = "TABLE";
  
  public BreakpointTable getTable() {
    return myTable;
  }

  public BreakpointTree getTree() {
    return myTree;
  }

  public void switchViews() {
    showView(isTreeShowing() ? TABLE_VIEW : TREE_VIEW);
  }

  public void showView(final String viewId) {
    if (TREE_VIEW.equals(viewId) || TABLE_VIEW.equals(viewId)) {
      myCurrentViewId = viewId;
      ((CardLayout)myTablePlace.getLayout()).show(myTablePlace, viewId);
      updateButtons();
      ensureSelectionExists();
    }
  }

  public String getCurrentViewId() {
    return myCurrentViewId;
  }

  public boolean isTreeShowing() {
    return BreakpointPanel.TREE_VIEW.equals(getCurrentViewId());
  }

  public interface ChangesListener extends EventListener {
    void breakpointsChanged();
  }

  public void addChangesListener(ChangesListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeChangesListener(ChangesListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public BreakpointPanel(BreakpointPropertiesPanel propertiesPanel, final BreakpointPanelAction[] actions) {
    myPropertiesPanel = propertiesPanel;
    myActions = actions;

    myTable = new BreakpointTable();
    myTree = new BreakpointTree();

    myTablePlace.setLayout(new CardLayout());
    myTablePlace.add(ScrollPaneFactory.createScrollPane(myTable), TABLE_VIEW);

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateCurrentBreakpointPropertiesPanel();
      }
    });
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateCurrentBreakpointPropertiesPanel();
      }
    });
    myTable.getModel().addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        if (e.getType() == TableModelEvent.UPDATE) {
          updateCurrentBreakpointPropertiesPanel();
        }
      }
    });
    myTree.getModel().addTreeModelListener(new TreeModelListener() {
      public void treeNodesChanged(TreeModelEvent e) {
      }

      public void treeNodesInserted(TreeModelEvent e) {
      }

      public void treeNodesRemoved(TreeModelEvent e) {
      }

      public void treeStructureChanged(TreeModelEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            ensureSelectionExists();
            updateButtons();
          }
        });
      }
    });
    myPropertiesPanelPlace.setLayout(new CardLayout());
    final JPanel stubPanel = new JPanel();
    stubPanel.setMinimumSize(myPropertiesPanel.getPanel().getMinimumSize());
    myPropertiesPanelPlace.add(stubPanel, PROPERTIES_STUB);
    myPropertiesPanelPlace.add(myPropertiesPanel.getPanel(), PROPERTIES_DATA);

    myBreakPointsPanel.setBorder(IdeBorderFactory.createEmptyBorder(6, 6, 0, 6));

    myButtonsPanel.setLayout(new GridBagLayout());
    for (int idx = 0; idx < actions.length; idx++) {
      final BreakpointPanelAction action = actions[idx];
      action.setPanel(this);
      final AbstractButton button = action.isStateAction()? new JCheckBox(action.getName()) : new JButton(action.getName());
      action.setButton(button);
      button.addActionListener(action);
      final double weighty = (idx == actions.length - 1) ? 1.0 : 0.0;
      myButtonsPanel.add(button, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, weighty, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 2, 2, 2), 0, 0));
    }
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });

    myTable.getModel().addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        myEventDispatcher.getMulticaster().breakpointsChanged();
      }
    });

    myTablePlace.add(ScrollPaneFactory.createScrollPane(myTree), TREE_VIEW);

    updateCurrentBreakpointPropertiesPanel();
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
      final AbstractButton button = action.getButton();
      action.update();
      if (!button.isEnabled() && button.hasFocus()) {
        button.transferFocus();
      }
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void selectBreakpoint(Breakpoint breakpoint) {
    int index = myTable.getModel().getBreakpointIndex(breakpoint);
    ListSelectionModel model = myTable.getSelectionModel();
    model.clearSelection();
    model.addSelectionInterval(index, index);
  }

  public void setBreakpoints(Breakpoint[] breakpoints) {
    myTable.setBreakpoints(breakpoints);
    myTree.setBreakpoints(breakpoints);
    ensureSelectionExists();
    updateButtons();
  }

  public Breakpoint[] getSelectedBreakpoints() {
    if (isTreeShowing()) {
      return myTree.getSelectedBreakpoints();
    }
    return myTable.getSelectedBreakpoints();
  }

  public void removeSelectedBreakpoints() {
    final Breakpoint[] selectedBreakpoints = getSelectedBreakpoints();
    myTree.removeBreakpoints(selectedBreakpoints);
    myTable.getModel().removeBreakpoints(selectedBreakpoints);
    myCurrentViewableBreakpoint = null;
    updateCurrentBreakpointPropertiesPanel();
  }

  public void insertBreakpointAt(Breakpoint breakpoint, int index) {
    myTable.getModel().insertBreakpointAt(breakpoint, index);
    myTree.addBreakpoint(breakpoint);
    selectBreakpoint(breakpoint);
  }

  public void addBreakpoint(Breakpoint breakpoint) {
    myTable.getModel().addBreakpoint(breakpoint);
    myTree.addBreakpoint(breakpoint);
    selectBreakpoint(breakpoint);
  }

  private void updateCurrentBreakpointPropertiesPanel() {
    if (myCurrentViewableBreakpoint != null) {
      myPropertiesPanel.saveTo(myCurrentViewableBreakpoint, new Runnable() {
        public void run() {
          if (isTreeShowing()) {
            myTree.repaint();
          }
          else {
            myTable.repaint();
          }
        }
      });
    }
    Breakpoint[] breakpoints = getSelectedBreakpoints();
    Breakpoint oldViewableBreakpoint = myCurrentViewableBreakpoint;
    myCurrentViewableBreakpoint = (breakpoints != null && breakpoints.length == 1) ? breakpoints[0] : null;
    if (myCurrentViewableBreakpoint != null) {
      if (oldViewableBreakpoint == null) {
        ((CardLayout)myPropertiesPanelPlace.getLayout()).show(myPropertiesPanelPlace, PROPERTIES_DATA);
      }
      myPropertiesPanel.initFrom(myCurrentViewableBreakpoint);
    }
    else {
      ((CardLayout)myPropertiesPanelPlace.getLayout()).show(myPropertiesPanelPlace, PROPERTIES_STUB);
    }
    updateButtons();
  }

  public JComponent getControl(String control) {
    return myPropertiesPanel.getControl(control);
  }

  public int getBreakpointCount() {
    return myTable.getBreakpoints().size();
  }

  public final java.util.List<Breakpoint> getBreakpoints() {
    return myTable.getBreakpoints();
  }

  public void dispose() {
    myPropertiesPanel.dispose();
  }

  public OpenFileDescriptor createEditSourceDescriptor(final Project project) {
    Breakpoint[] breakpoints = getSelectedBreakpoints();
    if (breakpoints == null || breakpoints.length == 0) {
      return null;
    }
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
    if (line < 0 || line >= doc.getLineCount()) {
      return null;
    }
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

  public void ensureSelectionExists() {
    if (myTable.getRowCount() > 0 && myTable.getSelectedRow() < 0) {
      ListSelectionModel model = myTable.getSelectionModel();
      model.clearSelection();
      model.addSelectionInterval(0, 0);
    }

    final java.util.List<Breakpoint> treeBreakpoints = myTree.getBreakpoints();
    if (treeBreakpoints.size() > 0) {
      final int[] rows = myTree.getSelectionRows();
      if (rows == null || rows.length == 0) {
        myTree.selectFirstBreakpoint();
      }
    }
    
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (isTreeShowing()) {
          myTree.requestFocus();
        }
        else {
          myTable.requestFocus();
        }
      }
    });
  }

}