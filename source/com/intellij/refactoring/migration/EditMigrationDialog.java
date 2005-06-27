
package com.intellij.refactoring.migration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class EditMigrationDialog extends DialogWrapper{
  private JTable myTable;
  private JButton myEditButton;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JButton myMoveUpButton;
  private JButton myMoveDownButton;
  private JTextField myNameField;
  private JTextArea myDescriptionTextArea;
  private Project myProject;
  private MigrationMap myMigrationMap;

  public EditMigrationDialog(Project project, MigrationMap migrationMap) {
    super(project, true);
    myProject = project;
    myMigrationMap = migrationMap;
    setHorizontalStretch(1.2f);
    setTitle("Edit Migration Map");
    init();
    validateOKButton();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private void validateOKButton() {
    boolean isEnabled = true;
    if (myNameField.getText().trim().length() == 0) {
      isEnabled = false;
    } else if (myMigrationMap.getEntryCount() == 0) {
      isEnabled = false;
    }
    setOKActionEnabled(isEnabled);
  }

  public String getName() {
    return myNameField.getText();
  }

  public String getDescription() {
    return myDescriptionTextArea.getText();
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    JLabel promptLabel = new JLabel("Map name:");
    panel.add(promptLabel, gbConstraints);

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    myNameField = new JTextField(myMigrationMap.getName());
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        validateOKButton();
      }
    });
    panel.add(myNameField, gbConstraints);

    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
    gbConstraints.anchor = GridBagConstraints.EAST;
    JLabel descriptionPromptLabel = new JLabel("Map description:");
    panel.add(descriptionPromptLabel, gbConstraints);

    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;

    myDescriptionTextArea = new JTextArea(myMigrationMap.getDescription(), 3, 40);
    myDescriptionTextArea.setLineWrap(true);
    myDescriptionTextArea.setWrapStyleWord(true);
    JScrollPane scrollPane = new JScrollPane(myDescriptionTextArea);
    scrollPane.setBorder(null);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
    myDescriptionTextArea.setFont(myNameField.getFont());
    myDescriptionTextArea.setBackground(myNameField.getBackground());
    scrollPane.setBorder(myNameField.getBorder());
    panel.add(scrollPane, gbConstraints);

    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.setBorder(IdeBorderFactory.createBorder());
    tablePanel.add(createTable(), BorderLayout.CENTER);

    JPanel tableButtonsPanel = new JPanel();
    tableButtonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    tableButtonsPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.insets = new Insets(5,0,5,0);

    myAddButton = new JButton("Add...");
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateOKButton();
      }
    });
    tableButtonsPanel.add(myAddButton, gbConstraints);

    myEditButton = new JButton("Edit...");
    tableButtonsPanel.add(myEditButton, gbConstraints);
    myRemoveButton = new JButton("Remove");
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateOKButton();
      }
    });
    tableButtonsPanel.add(myRemoveButton, gbConstraints);

    myMoveUpButton = new JButton("Move Up");
    tableButtonsPanel.add(myMoveUpButton, gbConstraints);
    myMoveDownButton = new JButton("Move Down");
    tableButtonsPanel.add(myMoveDownButton, gbConstraints);

    gbConstraints.weighty = 1;
    tableButtonsPanel.add(new JPanel(), gbConstraints);

    tablePanel.add(tableButtonsPanel, BorderLayout.EAST);

    myEditButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          edit();
        }
      }
    );

    myAddButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          addRow();
        }
      }
    );

    myRemoveButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          removeRow();
        }
      }
    );

    myMoveUpButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          moveUp();
        }
      }
    );

    myMoveDownButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          moveDown();
        }
      }
    );

    myTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener(){
        public void valueChanged(ListSelectionEvent e){
          enableButtons();
        }
      }
    );

    enableButtons();
    return tablePanel;
  }

  private void edit() {
    EditMigrationEntryDialog dialog = new EditMigrationEntryDialog(myProject);
    int selected = myTable.getSelectedRow();
    if (selected < 0)
      return;
    MigrationMapEntry entry = myMigrationMap.getEntryAt(selected);
    dialog.setEntry(entry);
    dialog.show();
    if (!dialog.isOK()){
      return;
    }
    dialog.updateEntry(entry);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsUpdated(selected, selected);
  }

  private void addRow() {
    EditMigrationEntryDialog dialog = new EditMigrationEntryDialog(myProject);
    MigrationMapEntry entry = new MigrationMapEntry();
    dialog.setEntry(entry);
    dialog.show();
    if (!dialog.isOK()){
      return;
    }
    dialog.updateEntry(entry);
    myMigrationMap.addEntry(entry);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsInserted(myMigrationMap.getEntryCount() - 1, myMigrationMap.getEntryCount() - 1);
    myTable.setRowSelectionInterval(myMigrationMap.getEntryCount() - 1, myMigrationMap.getEntryCount() - 1);
  }

  private void removeRow() {
    int selected = myTable.getSelectedRow();
    if (selected < 0)
      return;
    myMigrationMap.removeEntryAt(selected);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsDeleted(selected, selected);
    if (selected >= myMigrationMap.getEntryCount()){
      selected--;
    }
    if (selected >= 0){
      myTable.setRowSelectionInterval(selected, selected);
    }
  }

  private void moveUp() {
    int selected = myTable.getSelectedRow();
    if (selected < 1)
      return;
    MigrationMapEntry entry = myMigrationMap.getEntryAt(selected);
    MigrationMapEntry previousEntry = myMigrationMap.getEntryAt(selected - 1);
    myMigrationMap.setEntryAt(previousEntry, selected);
    myMigrationMap.setEntryAt(entry, selected - 1);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsUpdated(selected - 1, selected);
    myTable.setRowSelectionInterval(selected - 1, selected - 1);
  }

  private void moveDown() {
    int selected = myTable.getSelectedRow();
    if (selected >= myMigrationMap.getEntryCount() - 1)
      return;
    MigrationMapEntry entry = myMigrationMap.getEntryAt(selected);
    MigrationMapEntry nextEntry = myMigrationMap.getEntryAt(selected + 1);
    myMigrationMap.setEntryAt(nextEntry, selected);
    myMigrationMap.setEntryAt(entry, selected + 1);
    AbstractTableModel model = (AbstractTableModel)myTable.getModel();
    model.fireTableRowsUpdated(selected, selected + 1);
    myTable.setRowSelectionInterval(selected + 1, selected + 1);
  }

  private JScrollPane createTable() {
    final String[] names = {"Type", "Old name", "New name"};

    // Create a model of the data.
    TableModel dataModel = new AbstractTableModel() {
      public int getColumnCount() {
        return 3;
      }

      public int getRowCount() {
        return myMigrationMap.getEntryCount();
      }

      public Object getValueAt(int row, int col) {
        MigrationMapEntry entry = myMigrationMap.getEntryAt(row);
        if (col == 0){
          if (entry.getType() == MigrationMapEntry.PACKAGE && entry.isRecursive()){
            return "Package with subpackages";
          }
          else if (entry.getType() == MigrationMapEntry.PACKAGE && !entry.isRecursive()){
            return "Package";
          }
          else{
            return "Class";
          }
        }

        String suffix = (entry.getType() == MigrationMapEntry.PACKAGE ? ".*" : "");
        if (col == 1){
          return entry.getOldName() + suffix;
        }
        else{
          return entry.getNewName() + suffix;
        }
      }

      public String getColumnName(int column) {
        return names[column];
      }

      public Class getColumnClass(int c) {
        return String.class;
      }

      public boolean isCellEditable(int row, int col) {
        return false;
      }

      public void setValueAt(Object aValue, int row, int column) {
      }
    };

    // Create the table
    myTable = new Table(dataModel);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myTable.setPreferredScrollableViewportSize(new Dimension(300, myTable.getRowHeight() * 10));

    JScrollPane scrollpane = ScrollPaneFactory.createScrollPane(myTable);
    return scrollpane;
  }

  private void enableButtons() {
    int selectedIndex = myTable.getSelectedRow();
    myEditButton.setEnabled(selectedIndex != -1);
    myRemoveButton.setEnabled(selectedIndex != -1);
    myMoveDownButton.setEnabled(selectedIndex != -1 && selectedIndex < myTable.getRowCount() - 1);
    myMoveUpButton.setEnabled(selectedIndex > 0);
  }

}

