package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.macro.MacroFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 *
 */
class EditVariableDialog extends DialogWrapper {
  private ArrayList myVariables = new ArrayList();

  private JButton myMoveUpButton;
  private JButton myMoveDownButton;

  private JTable myTable;
  private Editor myEditor;
  private boolean hasMoveVars;
  private java.util.List<Macro> additionalMacros;

  public EditVariableDialog(Editor editor, Component parent, ArrayList variables, boolean _hasMoveVars, java.util.List<Macro> _additionalMacros) {
    super(parent, true);

    hasMoveVars = _hasMoveVars;
    additionalMacros = _additionalMacros;
    setButtonsMargin(null);
    myVariables = variables;
    myEditor = editor;
    init();
    setTitle("Edit Template Variables");
    setOKButtonText("OK");
    updateButtons();
  }

  public EditVariableDialog(Editor editor, Component parent, ArrayList variables) {
    this(editor,parent,variables,true,null);
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.templates.defineTemplates.editTemplVars");
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.codeInsight.template.impl.EditVariableDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder("Variables"));
    JPanel tablePanel = panel;
    tablePanel.setLayout(new BorderLayout());
    tablePanel.add(createVariablesTable(), BorderLayout.CENTER);
    if (hasMoveVars) tablePanel.add(createTableButtonPanel(), BorderLayout.EAST);
    return tablePanel;
  }

  private JPanel createTableButtonPanel() {
    JPanel tableButtonsPanel = new JPanel();
    tableButtonsPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    tableButtonsPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.insets = new Insets(0, 0, 4, 0);
    myMoveUpButton = new JButton("Move Up");
//    myMoveUpButton.setMargin(new Insets(2,0,0,2));
    myMoveUpButton.setMnemonic('U');
    tableButtonsPanel.add(myMoveUpButton, gbConstraints);
    myMoveDownButton = new JButton("Move Down");
//    myMoveDownButton.setMargin(new Insets(2,0,0,2));
    myMoveDownButton.setMnemonic('D');
    tableButtonsPanel.add(myMoveDownButton, gbConstraints);

    gbConstraints.weighty = 1;
    tableButtonsPanel.add(new JPanel(), gbConstraints);

    myMoveUpButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          moveRowUp();
        }
      }
    );

    myMoveDownButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          moveRowDown();
        }
      }
    );

    return tableButtonsPanel;
  }

  private JComponent createVariablesTable() {
    final String[] names = {"Name", "Expression", "Default value", "Skip if defined"};
    // Create a model of the data.
    TableModel dataModel = new AbstractTableModel() {
      public int getColumnCount() {
        return names.length;
      }

      public int getRowCount() {
        return myVariables.size();
      }

      public Object getValueAt(int row, int col) {
        Variable variable = (Variable)myVariables.get(row);
        if (col == 0) {
          return variable.getName();
        }
        if (col == 1) {
          return variable.getExpressionString();
        }
        if (col == 2) {
          return variable.getDefaultValueString();
        }
        else {
          return variable.isAlwaysStopAt() ? Boolean.FALSE : Boolean.TRUE;
        }
      }

      public String getColumnName(int column) {
        return names[column];
      }

      public Class getColumnClass(int c) {
        if (c <= 2) {
          return String.class;
        }
        else {
          return Boolean.class;
        }
      }

      public boolean isCellEditable(int row, int col) {
        return true;
      }

      public void setValueAt(Object aValue, int row, int col) {
        Variable variable = (Variable)myVariables.get(row);
        if (col == 0) {
           String varName = (String) aValue;
          Variable newVar = new Variable (varName, variable.getExpressionString(), variable.getDefaultValueString(),
                          variable.isAlwaysStopAt());
          myVariables.set(row, newVar);
          updateTemplateTextByVarNameChange(variable, newVar);
        }
        else if (col == 1) {
          variable.setExpressionString((String)aValue);
        }
        else if (col == 2) {
          variable.setDefaultValueString((String)aValue);
        }
        else {
          variable.setAlwaysStopAt(!((Boolean)aValue).booleanValue());
        }
      }
    };

    // Create the table
    myTable = new Table(dataModel);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setPreferredScrollableViewportSize(new Dimension(500, myTable.getRowHeight() * 8));
    myTable.getColumn(names[0]).setPreferredWidth(120);
    myTable.getColumn(names[1]).setPreferredWidth(200);
    myTable.getColumn(names[2]).setPreferredWidth(200);
    myTable.getColumn(names[3]).setPreferredWidth(100);
    if (myVariables.size() > 0) {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }

    JComboBox comboField = new JComboBox();
    Macro[] macros = MacroFactory.getMacros();

    if (additionalMacros!=null) {
      ArrayList list = new ArrayList(macros.length + additionalMacros.size());
      list.addAll( Arrays.asList(macros) );
      list.addAll( additionalMacros );
      macros = (Macro[])list.toArray(new Macro[0]);
    }

    Arrays.sort(macros, new Comparator<Macro> () {
      public int compare(Macro m1, Macro m2) {
        return m1.getDescription().compareTo(m2.getDescription());
      }
    });
    for (int i = 0; i < macros.length; i++) {
      Macro macro = macros[i];
      comboField.addItem(macro.getDescription());
    }
    comboField.setEditable(true);
    DefaultCellEditor cellEditor = new DefaultCellEditor(comboField);
    cellEditor.setClickCountToStart(1);
    myTable.getColumn(names[1]).setCellEditor(cellEditor);
    myTable.setRowHeight(comboField.getPreferredSize().height);

    JTextField textField = new JTextField();

    /*textField.addMouseListener(
      new PopupHandler(){
        public void invokePopup(Component comp,int x,int y){
          showCellPopup((JTextField)comp,x,y);
        }
      }
    );*/

    cellEditor = new DefaultCellEditor(textField);
    cellEditor.setClickCountToStart(1);
    myTable.setDefaultEditor(String.class, cellEditor);

    myTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateButtons();
        }
      }
    );
    JScrollPane scrollpane = ScrollPaneFactory.createScrollPane(myTable);
    return scrollpane;
  }

  private void updateButtons() {
    int selected = myTable.getSelectedRow();
    if (myMoveUpButton != null) {
      myMoveUpButton.setEnabled(selected >= 1);
    }
    if (myMoveDownButton != null) {
      myMoveDownButton.setEnabled(selected >= 0 && selected < myVariables.size() - 1);
    }
  }

  private void moveRowUp() {
    int selected = myTable.getSelectedRow();
    if (selected < 1) {
      return;
    }
    if (myTable.isEditing()) {
      TableCellEditor editor = myTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    moveElementUp(myVariables, selected);

    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsUpdated(selected - 1, selected);
    myTable.setRowSelectionInterval(selected - 1, selected - 1);
  }

  private static void moveElementUp(ArrayList array, int offset) {
    Object element = array.get(offset);
    Object previousElement = array.get(offset - 1);
    array.set(offset, previousElement);
    array.set(offset - 1, element);
  }

  private void moveRowDown() {
    int selected = myTable.getSelectedRow();
    if (selected >= myVariables.size() - 1 || selected < 0) {
      return;
    }
    if (myTable.isEditing()) {
      TableCellEditor editor = myTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    moveElementDown(myVariables, selected);

    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsUpdated(selected, selected + 1);
    myTable.setRowSelectionInterval(selected + 1, selected + 1);
  }

  private static void moveElementDown(ArrayList array, int offset) {
    Object element = array.get(offset);
    Object nextElement = array.get(offset + 1);
    array.set(offset, nextElement);
    array.set(offset + 1, element);
  }

  protected void doOKAction() {
    if (myTable.isEditing()) {
      TableCellEditor editor = myTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    super.doOKAction();
  }

  /*private void showCellPopup(final JTextField field,int x,int y) {
    JPopupMenu menu = new JPopupMenu();
    final Macro[] macros = MacroFactory.getMacros();
    for (int i = 0; i < macros.length; i++) {
      final Macro macro = macros[i];
      JMenuItem item = new JMenuItem(macro.getName());
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          try {
            field.saveToString().insertString(field.getCaretPosition(), macro.getName() + "()", null);
          }
          catch (BadLocationException e1) {
            LOG.error(e1);
          }
        }
      });
      menu.add(item);
    }
    menu.show(field, x, y);
  }*/

  private void updateTemplateTextByVarNameChange(final Variable oldVar, final Variable newVar) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(null, new Runnable() {
          public void run() {
            Document document = myEditor.getDocument();
            String templateText = document.getText();
            templateText = templateText.replaceAll("\\$" + oldVar.getName() + "\\$", "\\$" + newVar.getName() + "\\$");
            document.replaceString(0, document.getTextLength(), templateText);
          }
        }, null, null);
      }
    });
  }
}
