// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.testIntegration;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.JBUI;
import com.jetbrains.python.PyBundle;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public final class CreateTestDialog extends DialogWrapper {
  @NotNull
  private final PyTestCreationModel myModel;
  private final boolean myClassRequired;
  private TextFieldWithBrowseButton myTargetDir;
  private JTextField myClassName;
  private JPanel myMainPanel;
  private JTextField myFileName;
  private JTable myMethodsTable;
  @NotNull
  private final DefaultTableModel myTableModel;

  private CreateTestDialog(@NotNull final Project project, @NotNull final PyTestCreationModel model) {
    super(project);
    init();
    myClassRequired = StringUtil.isNotEmpty(model.getClassName());
    myModel = model;
    myTargetDir.addBrowseFolderListener(PyBundle.message("code.insight.select.target.directory"), null, project,
                                        FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myTargetDir.setEditable(false);


    myTargetDir.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        getOKAction().setEnabled(isValid());
      }
    });

    setTitle(PyBundle.message("code.insight.create.test"));

    addUpdater(myFileName);
    addUpdater(myClassName);

    //Fill UI with model
    myTargetDir.setText(model.getTargetDir());
    myFileName.setText(model.getFileName());
    final String clazz = model.getClassName();
    myClassName.setText(clazz);
    final List<String> methods = model.getMethods();
    final String[] columnNames = new String[]{"", "Test function"};
    final int columnWithCheckbox = 0;
    myTableModel = new DefaultTableModel(
      methods.stream().map(name -> new Object[]{Boolean.FALSE, name}).toArray(size -> new Object[size][columnNames.length]),
      columnNames
    ) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return column == columnWithCheckbox;
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == columnWithCheckbox ? Boolean.class : String.class;
      }
    };

    // Support "invert all selected with space"
    final String actionName = "InvertSelected";
    myMethodsTable.getInputMap().put(KeyStroke.getKeyStroke("SPACE"), actionName);
    myMethodsTable.getActionMap().put(actionName, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        for (int selectedRow : myMethodsTable.getSelectedRows()) {
          int row = myMethodsTable.convertRowIndexToModel(selectedRow);
          boolean value = !(Boolean)myTableModel.getValueAt(row, columnWithCheckbox);
          myTableModel.setValueAt(value, row, columnWithCheckbox);
        }
      }
    });

    // If only one method, then select it by default
    if (methods.size() == 1) {
      myTableModel.setValueAt(Boolean.TRUE, myTableModel.getRowCount() - 1, columnWithCheckbox);
    }
    myMethodsTable.setModel(myTableModel);
    myMethodsTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    TableUtil.setupCheckboxColumn(myMethodsTable, columnWithCheckbox);

    getOKAction().setEnabled(isValid());
  }

  static boolean userAcceptsTestCreation(@NotNull final Project project, @NotNull final PyTestCreationModel model) {
    final CreateTestDialog dialog = new CreateTestDialog(project, model);
    if (!dialog.showAndGet()) {
      return false;
    }
    dialog.copyToModel();
    return true;
  }

  private void copyToModel() {
    myModel.setClassName(myClassName.getText());
    myModel.setFileName(myFileName.getText());
    myModel.setTargetDir(myTargetDir.getText());
    @SuppressWarnings({"unchecked", "rawtypes", "UseOfObsoleteCollectionType"})
    Vector<Vector<Object>> dataVector = (Vector)myTableModel.getDataVector();
    StreamEx<Vector<Object>> methods = StreamEx.of(dataVector.stream());
    myModel.setMethods(new ArrayList<>(methods.map(v -> (v.get(0) == Boolean.TRUE) ? v.get(1).toString() : null).nonNull().toList()));
  }

  private void addUpdater(JTextField field) {
    field.getDocument().addDocumentListener(new MyDocumentListener());
  }

  private class MyDocumentListener implements DocumentListener {
    @Override
    public void insertUpdate(DocumentEvent documentEvent) {
      getOKAction().setEnabled(isValid());
    }

    @Override
    public void removeUpdate(DocumentEvent documentEvent) {
      getOKAction().setEnabled(isValid());
    }

    @Override
    public void changedUpdate(DocumentEvent documentEvent) {
      getOKAction().setEnabled(isValid());
    }
  }

  private boolean isValid() {
    return !StringUtil.isEmptyOrSpaces(getTargetDir())
           && (!myClassRequired || !StringUtil.isEmptyOrSpaces(getClassName()))
           && !StringUtil.isEmptyOrSpaces(getFileName());
  }

  @Override
  protected JComponent createCenterPanel() {
    myMainPanel.setBorder(JBUI.Borders.empty());
    return myMainPanel;
  }

  private String getTargetDir() {
    return myTargetDir.getText().trim();
  }

  private String getClassName() {
    return myClassName.getText().trim();
  }

  private String getFileName() {
    return myFileName.getText().trim();
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.createTestsFromGoTo";
  }
}
