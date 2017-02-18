/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.testIntegration;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.TableUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 */
public class CreateTestDialog extends DialogWrapper {
  private TextFieldWithBrowseButton myTargetDir;
  private JTextField myClassName;
  private JPanel myMainPanel;
  private JTextField myFileName;
  private JTable myMethodsTable;
  private DefaultTableModel myTableModel;

  protected CreateTestDialog(Project project) {
    super(project);
    init();
    myTargetDir.addBrowseFolderListener("Select target directory", null, project,
                                        FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myTargetDir.setEditable(false);


    myTargetDir.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        getOKAction().setEnabled(isValid());
      }
    });

    setTitle("Create test");

    addUpdater(myFileName);
    addUpdater(myClassName);

  }

  public void methodsSize(int methods) {
    myTableModel = new DefaultTableModel(methods, 2);
    myMethodsTable.setModel(myTableModel);

    TableColumn checkColumn = myMethodsTable.getColumnModel().getColumn(0);
    TableUtil.setupCheckboxColumn(checkColumn);
    checkColumn.setCellRenderer(new BooleanTableCellRenderer());
    checkColumn.setCellEditor(new DefaultCellEditor(new JCheckBox()));

    myMethodsTable.getColumnModel().getColumn(1).setHeaderValue("Test method");
    checkColumn.setHeaderValue("");
    getOKAction().setEnabled(true);
  }

  protected void addUpdater(JTextField field) {
      field.getDocument().addDocumentListener(new MyDocumentListener());
  }
  private class MyDocumentListener implements DocumentListener {
    public void insertUpdate(DocumentEvent documentEvent) {
      getOKAction().setEnabled(isValid());
    }

    public void removeUpdate(DocumentEvent documentEvent) {
      getOKAction().setEnabled(isValid());
    }

    public void changedUpdate(DocumentEvent documentEvent) {
      getOKAction().setEnabled(isValid());
    }
  }

  private boolean isValid() {
    return !StringUtil.isEmptyOrSpaces(getTargetDir()) && !StringUtil.isEmptyOrSpaces(getClassName())
      && !StringUtil.isEmptyOrSpaces(getFileName());
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public String getTargetDir() {
    return myTargetDir.getText().trim();
  }

  public void setTargetDir(String text) {
    myTargetDir.setText(text);
  }

  public void setClassName(String text) {
    myClassName.setText(text);
  }

  public void setFileName(String text) {
    myFileName.setText(text);
  }

  public String getClassName() {
    return myClassName.getText().trim();
  }

  public String getFileName() {
    return myFileName.getText().trim();
  }

  public void addMethod(String name, int row) {
    myTableModel.setValueAt(name, row, 1);
    myTableModel.setValueAt(Boolean.FALSE, row, 0);
  }

  public List<String> getMethods() {
    List<String> res = new ArrayList<>();

    for (int i = 0; i != myTableModel.getRowCount(); ++i) {
      Object val = myTableModel.getValueAt(i, 0);
      if (val != null && (Boolean)val == true)
        res.add((String)myTableModel.getValueAt(i, 1));
    }
    return res;
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.dialogs.createTestsFromGoTo");
  }
}
