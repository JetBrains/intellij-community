package com.intellij.application.options;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.*;
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

public class CodeStyleImportsPanel extends JPanel {
  private JCheckBox myCbUseFQClassNames;
  private JCheckBox myCbUseFQClassNamesInJavaDoc;
  private JCheckBox myCbUseSingleClassImports;
  private JCheckBox myCbInsertInnerClassImports;
  private JCheckBox myCbOptimizeImportsOnTheFly;
  private JCheckBox myCbAddUnambiguousImportsOnTheFly;
  private JTextField myClassCountField;
  private JTextField myNamesCountField;
  private CodeStyleSettings.ImportLayoutTable myImportLayoutList = new CodeStyleSettings.ImportLayoutTable();
  private CodeStyleSettings.PackageTable myPackageList = new CodeStyleSettings.PackageTable();
  private CodeStyleSettings.ImportLayoutTable.PackageEntry myOtherPackageEntry = null;

  private Table myImportLayoutTable;
  private JButton myMoveUpButton;
  private JButton myMoveDownButton;
  private JButton myRemovePackageFromImportLayoutButton;
  private JButton myRemovePackageFromPackagesButton;
  private Table myPackageTable;
  private CodeStyleSettings mySettings;
  private JRadioButton myJspImportCommaSeparated;
  private JRadioButton myJspOneImportPerDirective;

  public CodeStyleImportsPanel(CodeStyleSettings settings){
    mySettings = settings;
    setLayout(new GridBagLayout());

    setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));

    add(createGeneralOptionsPanel(),
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.VERTICAL,
                               new Insets(0, 0, 0, 0), 0, 0));

    add(createPackagesPanel(),
        new GridBagConstraints(1, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                               new Insets(0, 0, 0, 0), 0, 0));

    add(createImportLayoutPanel(),
        new GridBagConstraints(0, 1, 2, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                               new Insets(0, 0, 0, 0), 0, 0));

    add(createJspImportLayoutPanel(),
        new GridBagConstraints(0, 2, 2, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                               new Insets(0, 0, 0, 0), 0, 0));

    add(new MyTailPanel(),
        new GridBagConstraints(0, 3, 2, 1, 0, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                               new Insets(0, 0, 0, 0), 0, 0));
  }

  private JPanel createJspImportLayoutPanel() {
    ButtonGroup buttonGroup = new ButtonGroup();
    myJspImportCommaSeparated = new JRadioButton(ApplicationBundle.message("radio.prefer.comma.separated.import.list"));
    myJspOneImportPerDirective = new JRadioButton(ApplicationBundle.message("radio.prefer.one.import.statement.per.page.directive"));
    buttonGroup.add(myJspImportCommaSeparated);
    buttonGroup.add(myJspOneImportPerDirective);
    JPanel btnPanel = new JPanel(new BorderLayout());
    btnPanel.add(myJspImportCommaSeparated, BorderLayout.NORTH);
    btnPanel.add(myJspOneImportPerDirective, BorderLayout.CENTER);

    //noinspection HardCodedStringLiteral
    final MultiLineLabel commaSeparatedLabel = new MultiLineLabel("<% page import=\"com.company.Boo, \n" +
                                                                  "                 com.company.Far\"%>");
    //noinspection HardCodedStringLiteral
    final MultiLineLabel oneImportPerDirectiveLabel = new MultiLineLabel("<% page import=\"com.company.Boo\"%>\n" +
                                                                         "<% page import=\"com.company.Far\"%>");
    final JPanel labelPanel = new JPanel(new BorderLayout());
    labelPanel.setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.preview")));

    JPanel resultPanel = new JPanel(new BorderLayout());
    resultPanel.add(btnPanel, BorderLayout.NORTH);
    resultPanel.add(labelPanel, BorderLayout.CENTER);
    resultPanel.setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.jsp.imports.layout")));


    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean isComma = myJspImportCommaSeparated.isSelected();
        labelPanel.removeAll();
        labelPanel.add(isComma ? commaSeparatedLabel : oneImportPerDirectiveLabel, BorderLayout.CENTER);
        labelPanel.repaint();
        labelPanel.revalidate();
      }
    };
    myJspImportCommaSeparated.addActionListener(actionListener);
    myJspOneImportPerDirective.addActionListener(actionListener);
    return resultPanel;
  }

  private JPanel createGeneralOptionsPanel() {
    OptionGroup group = new OptionGroup(ApplicationBundle.message("title.general"));
    myCbUseSingleClassImports = new JCheckBox(ApplicationBundle.message("checkbox.use.single.class.import"));
    group.add(myCbUseSingleClassImports);

    myCbUseFQClassNames = new JCheckBox(ApplicationBundle.message("checkbox.use.fully.qualified.class.names"));
    group.add(myCbUseFQClassNames);

    myCbInsertInnerClassImports = new JCheckBox(ApplicationBundle.message("checkbox.insert.imports.for.inner.classes"));
    group.add(myCbInsertInnerClassImports);

    myCbUseFQClassNamesInJavaDoc = new JCheckBox(ApplicationBundle.message("checkbox.use.fully.qualified.class.names.in.javadoc"));
    group.add(myCbUseFQClassNamesInJavaDoc);

    myCbOptimizeImportsOnTheFly = new JCheckBox(ApplicationBundle.message("checkbox.optimize.imports.on.the.fly"));
    group.add(myCbOptimizeImportsOnTheFly);

    myCbAddUnambiguousImportsOnTheFly = new JCheckBox(ApplicationBundle.message("checkbox.add.unambiguous.imports.on.the.fly"));
    group.add(myCbAddUnambiguousImportsOnTheFly);

    myClassCountField = new JTextField(3);
    myNamesCountField = new JTextField(3);
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.add(new JLabel(ApplicationBundle.message("editbox.class.count.to.use.import.with.star")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 3, 0, 0), 0, 0));
    panel.add(myClassCountField, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 1, 0, 0), 0, 0));
    panel.add(new JLabel(ApplicationBundle.message("editbox.names.count.to.use.static.import.with.star")), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 3, 0, 0), 0, 0));
    panel.add(myNamesCountField, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 1, 0, 0), 0, 0));

    group.add(panel);
    return group.createPanel();
  }

  private JPanel createPackagesPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.packages.to.use.import.with")));

    panel.add(createPackagesTable(), BorderLayout.CENTER);
    panel.add(createPackagesButtonsPanel(), BorderLayout.EAST);
    return panel;
  }

  private JPanel createImportLayoutPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.import.layout")));
    panel.add(createImportLayoutTable(), BorderLayout.CENTER);
    panel.add(createImportLayoutButtonsPanel(), BorderLayout.EAST);
    return panel;
  }

  private JPanel createImportLayoutButtonsPanel() {
    JPanel tableButtonsPanel = new JPanel(new VerticalFlowLayout());

    JButton addPackageToImportLayoutButton = new JButton(ApplicationBundle.message("button.add.package"));
    tableButtonsPanel.add(addPackageToImportLayoutButton);

    JButton addBlankLineButton = new JButton(ApplicationBundle.message("button.add.blank"));
    tableButtonsPanel.add(addBlankLineButton);

    myMoveUpButton = new JButton(ApplicationBundle.message("button.move.up"));
    tableButtonsPanel.add(myMoveUpButton);

    myMoveDownButton = new JButton(ApplicationBundle.message("button.move.down"));
    tableButtonsPanel.add(myMoveDownButton);

    myRemovePackageFromImportLayoutButton = new JButton(ApplicationBundle.message("button.remove"));
    tableButtonsPanel.add(myRemovePackageFromImportLayoutButton);

    addPackageToImportLayoutButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          addPackageToImportLayouts();
        }
      }
    );

    addBlankLineButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          addBlankLine();
        }
      }
    );

    myRemovePackageFromImportLayoutButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          removeEntryFromImportLayouts();
        }
      }
    );

    myMoveUpButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          moveRowUp();
        }
      }
    );

    myMoveDownButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          moveRowDown();
        }
      }
    );

    return tableButtonsPanel;
  }

  private JPanel createPackagesButtonsPanel() {
    JPanel tableButtonsPanel = new JPanel(new VerticalFlowLayout());
    tableButtonsPanel.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));

    JButton addPackageToPackagesButton = new JButton(ApplicationBundle.message("button.add.package.p"));
    tableButtonsPanel.add(addPackageToPackagesButton);

    myRemovePackageFromPackagesButton = new JButton(ApplicationBundle.message("button.remove.r"));
    tableButtonsPanel.add(myRemovePackageFromPackagesButton);

    addPackageToPackagesButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          addPackageToPackages();
        }
      }
    );

    myRemovePackageFromPackagesButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e){
          removeEntryFromPackages();
        }
      }
    );

    return tableButtonsPanel;
  }

  private void addPackageToImportLayouts() {
    int selected = myImportLayoutTable.getSelectedRow() + 1;
    if(selected < 0) {
      selected = myImportLayoutList.getEntryCount();
    }
    CodeStyleSettings.ImportLayoutTable.PackageEntry entry = new CodeStyleSettings.ImportLayoutTable.PackageEntry("", true);
    myImportLayoutList.insertEntryAt(entry, selected);
    refreshTableModel(selected, myImportLayoutTable);
  }

  private static void refreshTableModel(int selectedRow, Table table) {
    AbstractTableModel model = (AbstractTableModel)table.getModel();
    model.fireTableRowsInserted(selectedRow, selectedRow);
    table.setRowSelectionInterval(selectedRow, selectedRow);
//    myImportLayoutTable.requestFocus();
//    myImportLayoutTable.editCellAt(selected, 0);
    TableUtil.editCellAt(table, selectedRow, 0);
    Component editorComp = table.getEditorComponent();
    if(editorComp != null) {
      editorComp.requestFocus();
    }
  }

  private void addPackageToPackages() {
    int selected = myPackageTable.getSelectedRow() + 1;
    if(selected < 0) {
      selected = myPackageList.getEntryCount();
    }
    CodeStyleSettings.PackageTable.Entry entry = new CodeStyleSettings.PackageTable.Entry("", true);
    myPackageList.insertEntryAt(entry, selected);
    refreshTableModel(selected, myPackageTable);
  }

  private void addBlankLine() {
    int selected = myImportLayoutTable.getSelectedRow() + 1;
    if(selected < 0) {
      selected = myImportLayoutList.getEntryCount();
    }
    CodeStyleSettings.ImportLayoutTable.EmptyLineEntry entry = new CodeStyleSettings.ImportLayoutTable.EmptyLineEntry();
    myImportLayoutList.insertEntryAt(entry, selected);
    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsInserted(selected, selected);
    myImportLayoutTable.setRowSelectionInterval(selected, selected);
  }

  private void removeEntryFromImportLayouts() {
    int selected = myImportLayoutTable.getSelectedRow();
    if(selected < 0)
      return;
    CodeStyleSettings.ImportLayoutTable.Entry entry = myImportLayoutList.getEntryAt(selected);
    if(isOtherEntry(entry)) {
      boolean isFound = false;
      CodeStyleSettings.ImportLayoutTable.Entry[] entries = myImportLayoutList.getEntries();
      for(int i = 0; i < entries.length; i++){
        if(i != selected && isOtherEntry(entries[i])) {
          isFound = true;
          break;
        }
      }
      if(!isFound) {
        return;
      }
    }
    if(myImportLayoutTable.isEditing()) {
      TableCellEditor editor = myImportLayoutTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    myImportLayoutList.removeEntryAt(selected);
    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsDeleted(selected, selected);
    if(selected >= myImportLayoutList.getEntryCount()) {
      selected --;
    }
    if(selected >= 0) {
      myImportLayoutTable.setRowSelectionInterval(selected, selected);
    }
  }

  private void removeEntryFromPackages() {
    int selected = myPackageTable.getSelectedRow();
    if(selected < 0)
      return;
    if(myPackageTable.isEditing()) {
      TableCellEditor editor = myPackageTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    myPackageList.removeEntryAt(selected);
    AbstractTableModel model = (AbstractTableModel)myPackageTable.getModel();
    model.fireTableRowsDeleted(selected, selected);
    if(selected >= myPackageList.getEntryCount()) {
      selected --;
    }
    if(selected >= 0) {
      myPackageTable.setRowSelectionInterval(selected, selected);
    }
  }

  private void moveRowUp() {
    int selected = myImportLayoutTable.getSelectedRow();
    if(selected < 1) {
      return;
    }
    if(myImportLayoutTable.isEditing()) {
      TableCellEditor editor = myImportLayoutTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    CodeStyleSettings.ImportLayoutTable.Entry entry = myImportLayoutList.getEntryAt(selected);
    CodeStyleSettings.ImportLayoutTable.Entry previousEntry = myImportLayoutList.getEntryAt(selected-1);
    myImportLayoutList.setEntryAt(previousEntry, selected);
    myImportLayoutList.setEntryAt(entry, selected-1);

    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsUpdated(selected-1, selected);
    myImportLayoutTable.setRowSelectionInterval(selected-1, selected-1);
  }

  private void moveRowDown() {
    int selected = myImportLayoutTable.getSelectedRow();
    if(selected >= myImportLayoutList.getEntryCount()-1) {
      return;
    }
    if(myImportLayoutTable.isEditing()) {
      TableCellEditor editor = myImportLayoutTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    CodeStyleSettings.ImportLayoutTable.Entry entry = myImportLayoutList.getEntryAt(selected);
    CodeStyleSettings.ImportLayoutTable.Entry nextEntry = myImportLayoutList.getEntryAt(selected+1);
    myImportLayoutList.setEntryAt(nextEntry, selected);
    myImportLayoutList.setEntryAt(entry, selected+1);

    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableRowsUpdated(selected, selected+1);
    myImportLayoutTable.setRowSelectionInterval(selected+1, selected+1);
  }

  private JComponent createPackagesTable() {
    final String[] names = {
      ApplicationBundle.message("listbox.import.package"),
      ApplicationBundle.message("listbox.import.with.subpackages")
    };
    // Create a model of the data.
    TableModel dataModel = new AbstractTableModel() {
      public int getColumnCount() { return names.length; }
      public int getRowCount() { return myPackageList.getEntryCount();}
      public Object getValueAt(int row, int col) {
        CodeStyleSettings.PackageTable.Entry entry = myPackageList.getEntryAt(row);
        if(col == 0) {
          if(entry != null) {
            return entry.getPackageName();
          }
        }

        if(col == 1) {
          if(entry != null) {
            return entry.isWithSubpackages() ? Boolean.TRUE : Boolean.FALSE;
          }
        }
        return null;
      }
      public String getColumnName(int column) { return names[column]; }
      public Class getColumnClass(int c) {
        if(c == 0) {
          return String.class;
        }
        if(c == 1) {
          return Boolean.class;
        }
        return null;
      }
      public boolean isCellEditable(int row, int col) {
        return true;
      }

      public void setValueAt(Object aValue, int row, int col) {
        CodeStyleSettings.PackageTable.Entry packageEntry = myPackageList.getEntryAt(row);
        if(col == 0) {
          CodeStyleSettings.PackageTable.Entry newPackageEntry = new CodeStyleSettings.PackageTable.Entry(((String)aValue).trim(), packageEntry.isWithSubpackages());
          myPackageList.setEntryAt(newPackageEntry, row);
        }

        if(col == 1) {
          CodeStyleSettings.PackageTable.Entry newPackageEntry = new CodeStyleSettings.PackageTable.Entry(packageEntry.getPackageName(), ((Boolean)aValue).booleanValue());
          myPackageList.setEntryAt(newPackageEntry, row);
        }
      }
    };

    // Create the table
    myPackageTable = new Table(dataModel);
    myPackageTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    TableCellEditor editor = myPackageTable.getDefaultEditor(String.class);
    myPackageTable.fixColumnWidthToHeader(1);
    if (editor instanceof DefaultCellEditor) {
      ((DefaultCellEditor)editor).setClickCountToStart(1);
    }
    myPackageTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener(){
        public void valueChanged(ListSelectionEvent e){
          updateButtons();
        }
      }
    );

    return ScrollPaneFactory.createScrollPane(myPackageTable);
  }

  private void updateButtons(){
    int selectedImport = myImportLayoutTable.getSelectedRow();
    myMoveUpButton.setEnabled(selectedImport >= 1);
    myMoveDownButton.setEnabled(selectedImport < myImportLayoutTable.getRowCount()-1);
    if(selectedImport < 0 || myOtherPackageEntry == myImportLayoutList.getEntryAt(selectedImport)) {
      myRemovePackageFromImportLayoutButton.setEnabled(false);
    }
    else {
      myRemovePackageFromImportLayoutButton.setEnabled(true);
    }

    int selectedPackage = myPackageTable.getSelectedRow();
    myRemovePackageFromPackagesButton.setEnabled(selectedPackage >= 0);

  }

  private static boolean isOtherEntry(CodeStyleSettings.ImportLayoutTable.Entry entry) {
    if(!(entry instanceof CodeStyleSettings.ImportLayoutTable.PackageEntry)) {
      return false;
    }
    CodeStyleSettings.ImportLayoutTable.PackageEntry packageEntry = (CodeStyleSettings.ImportLayoutTable.PackageEntry)entry;
    String packageName = packageEntry.getPackageName();
    return packageName.length() == 0 && packageEntry.isWithSubpackages();
  }

  private JComponent createImportLayoutTable() {
    final String[] names = {
      ApplicationBundle.message("listbox.import.package"),
      ApplicationBundle.message("listbox.import.with.subpackages")
    };
    // Create a model of the data.
    TableModel dataModel = new AbstractTableModel() {
      public int getColumnCount() { return names.length; }
      public int getRowCount() { return myImportLayoutList.getEntryCount();}
      public Object getValueAt(int row, int col) {
        CodeStyleSettings.ImportLayoutTable.Entry entry = myImportLayoutList.getEntryAt(row);
        if(col == 0) {
          if(isOtherEntry(entry) && entry == myOtherPackageEntry) {
            return ApplicationBundle.message("listbox.import.all.other.imports");
          }
          else if(entry instanceof CodeStyleSettings.ImportLayoutTable.PackageEntry) {
            CodeStyleSettings.ImportLayoutTable.PackageEntry packageEntry = (CodeStyleSettings.ImportLayoutTable.PackageEntry)entry;
            return packageEntry.getPackageName();
          }
          else {
            return ApplicationBundle.message("listbox.import.blank.line");
          }
        }

        if(col == 1) {
          if(isOtherEntry(entry) && entry == myOtherPackageEntry) {
            return null;
          }
          else if(entry instanceof CodeStyleSettings.ImportLayoutTable.PackageEntry) {
            CodeStyleSettings.ImportLayoutTable.PackageEntry packageEntry = (CodeStyleSettings.ImportLayoutTable.PackageEntry)entry;
            return packageEntry.isWithSubpackages() ? Boolean.TRUE : Boolean.FALSE;
          }
          else {
            return null;
          }
        }
        return null;
      }
      public String getColumnName(int column) { return names[column]; }
      public Class getColumnClass(int c) {
        if(c == 0) {
          return String.class;
        }
        if(c == 1) {
          return Boolean.class;
        }
        return null;
//        return CodeStyleSettings.ImportLayoutTable.Entry.class;
      }
      public boolean isCellEditable(int row, int col) {
        CodeStyleSettings.ImportLayoutTable.Entry entry = myImportLayoutList.getEntryAt(row);
        if (isOtherEntry(entry) && entry == myOtherPackageEntry) {
          return false;
        }
        return entry instanceof CodeStyleSettings.ImportLayoutTable.PackageEntry;
      }

      public void setValueAt(Object aValue, int row, int col) {
        CodeStyleSettings.ImportLayoutTable.Entry entry = myImportLayoutList.getEntryAt(row);
        if(col == 0 && entry instanceof CodeStyleSettings.ImportLayoutTable.PackageEntry) {
          CodeStyleSettings.ImportLayoutTable.PackageEntry packageEntry = (CodeStyleSettings.ImportLayoutTable.PackageEntry)entry;
          CodeStyleSettings.ImportLayoutTable.PackageEntry newPackageEntry = new CodeStyleSettings.ImportLayoutTable.PackageEntry(((String)aValue).trim(), packageEntry.isWithSubpackages());
          myImportLayoutList.setEntryAt(newPackageEntry, row);
        }
        if(col == 1 && entry instanceof CodeStyleSettings.ImportLayoutTable.PackageEntry) {
          CodeStyleSettings.ImportLayoutTable.PackageEntry packageEntry = (CodeStyleSettings.ImportLayoutTable.PackageEntry)entry;
          CodeStyleSettings.ImportLayoutTable.PackageEntry newPackageEntry = new CodeStyleSettings.ImportLayoutTable.PackageEntry(packageEntry.getPackageName(), aValue.equals(Boolean.TRUE));
          myImportLayoutList.setEntryAt(newPackageEntry, row);
        }
      }
    };

    // Create the table
    myImportLayoutTable = new Table(dataModel);
    myImportLayoutTable.setDefaultRenderer(Boolean.class, new BooleanTableCellRenderer());
    myImportLayoutTable.fixColumnWidthToHeader(1);
    myImportLayoutTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    TableCellEditor editor = myImportLayoutTable.getDefaultEditor(String.class);
    if (editor instanceof DefaultCellEditor) {
      ((DefaultCellEditor)editor).setClickCountToStart(1);
    }

    myImportLayoutTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener(){
        public void valueChanged(ListSelectionEvent e){
          updateButtons();
        }
      }
    );

    return ScrollPaneFactory.createScrollPane(myImportLayoutTable);
  }

  public void reset() {
    myCbUseFQClassNames.setSelected(mySettings.USE_FQ_CLASS_NAMES);
    myCbUseFQClassNamesInJavaDoc.setSelected(mySettings.USE_FQ_CLASS_NAMES_IN_JAVADOC);
    myCbUseSingleClassImports.setSelected(mySettings.USE_SINGLE_CLASS_IMPORTS);
    myCbInsertInnerClassImports.setSelected(mySettings.INSERT_INNER_CLASS_IMPORTS);
    myClassCountField.setText(Integer.toString(mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND));
    myNamesCountField.setText(Integer.toString(mySettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND));
    myCbAddUnambiguousImportsOnTheFly.setSelected(mySettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY);
    myCbOptimizeImportsOnTheFly.setSelected(mySettings.OPTIMIZE_IMPORTS_ON_THE_FLY);

    myImportLayoutList.copyFrom(mySettings.IMPORT_LAYOUT_TABLE);
    CodeStyleSettings.ImportLayoutTable.Entry[] entries = myImportLayoutList.getEntries();
    for (CodeStyleSettings.ImportLayoutTable.Entry entry : entries) {
      if (isOtherEntry(entry)) {
        myOtherPackageEntry = (CodeStyleSettings.ImportLayoutTable.PackageEntry)entry;
      }
    }
    myPackageList = new CodeStyleSettings.PackageTable();
    myPackageList.copyFrom(mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND);

    AbstractTableModel model = (AbstractTableModel)myImportLayoutTable.getModel();
    model.fireTableDataChanged();

    model = (AbstractTableModel)myPackageTable.getModel();
    model.fireTableDataChanged();

    if(myImportLayoutTable.getRowCount() > 0) {
      myImportLayoutTable.getSelectionModel().setSelectionInterval(0, 0);
    }
    if(myPackageTable.getRowCount() > 0) {
      myPackageTable.getSelectionModel().setSelectionInterval(0, 0);
    }

    if (mySettings.JSP_PREFER_COMMA_SEPARATED_IMPORT_LIST) {
      myJspImportCommaSeparated.doClick();
    }
    else {
      myJspOneImportPerDirective.doClick();
    }
    updateButtons();
  }

  public void apply() {
    stopTableEditing();

    mySettings.USE_FQ_CLASS_NAMES = myCbUseFQClassNames.isSelected();
    mySettings.USE_FQ_CLASS_NAMES_IN_JAVADOC = myCbUseFQClassNamesInJavaDoc.isSelected();
    mySettings.USE_SINGLE_CLASS_IMPORTS = myCbUseSingleClassImports.isSelected();
    mySettings.INSERT_INNER_CLASS_IMPORTS = myCbInsertInnerClassImports.isSelected();
    mySettings.OPTIMIZE_IMPORTS_ON_THE_FLY = myCbOptimizeImportsOnTheFly.isSelected();
    mySettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = myCbAddUnambiguousImportsOnTheFly.isSelected();
    try{
      mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = Integer.parseInt(myClassCountField.getText());
    }
    catch(NumberFormatException e){
      //just a bad number
    }
    try{
      mySettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = Integer.parseInt(myNamesCountField.getText());
    }
    catch(NumberFormatException e){
      //just a bad number
    }

    mySettings.IMPORT_LAYOUT_TABLE.copyFrom(myImportLayoutList);

    CodeStyleSettings.ImportLayoutTable.Entry[] entries = myImportLayoutList.getEntries();
    int removedEntryCount = 0;
    for(int i = 0; i < entries.length; i++){
      CodeStyleSettings.ImportLayoutTable.Entry entry = entries[i];
      if(isOtherEntry(entry) && entry != myOtherPackageEntry) {
        mySettings.IMPORT_LAYOUT_TABLE.removeEntryAt(i-removedEntryCount);
        removedEntryCount++;
      }
    }

    mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND = myPackageList;

    mySettings.JSP_PREFER_COMMA_SEPARATED_IMPORT_LIST = myJspImportCommaSeparated.isSelected();
  }

  private void stopTableEditing() {
    if(myImportLayoutTable.isEditing()) {
      TableCellEditor editor = myImportLayoutTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    if(myPackageTable.isEditing()) {
      TableCellEditor editor = myPackageTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
  }

  public boolean isModified() {
    stopTableEditing();

    boolean isModified = isModified(myCbUseFQClassNames, mySettings.USE_FQ_CLASS_NAMES);
    isModified |= isModified(myCbUseFQClassNamesInJavaDoc, mySettings.USE_FQ_CLASS_NAMES_IN_JAVADOC);
    isModified |= isModified(myCbUseSingleClassImports, mySettings.USE_SINGLE_CLASS_IMPORTS);
    isModified |= isModified(myCbInsertInnerClassImports, mySettings.INSERT_INNER_CLASS_IMPORTS);
    isModified |= isModified(myCbOptimizeImportsOnTheFly, mySettings.OPTIMIZE_IMPORTS_ON_THE_FLY);
    isModified |= isModified(myCbAddUnambiguousImportsOnTheFly, mySettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY);
    isModified |= isModified(myClassCountField, mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND);
    isModified |= isModified(myNamesCountField, mySettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND);

    isModified |= isModified(myImportLayoutList, mySettings.IMPORT_LAYOUT_TABLE);
    isModified |= isModified(myPackageList, mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND);
    isModified |= mySettings.JSP_PREFER_COMMA_SEPARATED_IMPORT_LIST != myJspImportCommaSeparated.isSelected();

    return isModified;
  }

  private static boolean isModified(JTextField textField, int value) {
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch(NumberFormatException e) {
      return false;
    }
  }

  private static boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isModified(CodeStyleSettings.ImportLayoutTable list, CodeStyleSettings.ImportLayoutTable table) {
    if(list.getEntryCount() != table.getEntryCount()) {
      return true;
    }

    for(int i=0; i<list.getEntryCount(); i++) {
      CodeStyleSettings.ImportLayoutTable.Entry entry1 = list.getEntryAt(i);
      CodeStyleSettings.ImportLayoutTable.Entry entry2 = table.getEntryAt(i);
      if(!entry1.equals(entry2)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isModified(CodeStyleSettings.PackageTable list, CodeStyleSettings.PackageTable table) {
    if(list.getEntryCount() != table.getEntryCount()) {
      return true;
    }

    for(int i=0; i<list.getEntryCount(); i++) {
      CodeStyleSettings.PackageTable.Entry entry1 = list.getEntryAt(i);
      CodeStyleSettings.PackageTable.Entry entry2 = table.getEntryAt(i);
      if(!entry1.equals(entry2)) {
        return true;
      }
    }

    return false;
  }

  private static class MyTailPanel extends JPanel {
    public Dimension getMinimumSize() {
      return new Dimension(0,0);
    }
    public Dimension getPreferredSize() {
      return new Dimension(0,0);
    }
  }
}