package com.intellij.debugger.ui;

import com.intellij.debugger.ClassFilter;
import com.intellij.debugger.ClassFilter;
import com.intellij.debugger.ClassFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

/**
 * User: lex
 * Date: Aug 29, 2003
 * Time: 2:38:30 PM
 */
public class InstanceFilterEditor extends ClassFilterEditor {
  public InstanceFilterEditor(Project project) {
    super(project);
    myAddPatternButton.setVisible(false);
  }

  protected void addClassFilter() {
    String idString = Messages.showInputDialog(myProject, "Enter instance ID:", "Add Instance Filter", Messages.getQuestionIcon());
    if (idString != null) {
      ClassFilter filter = createFilter(idString);
      if(filter != null){
        myTableModel.addRow(filter);
        int row = myTableModel.getRowCount() - 1;
        myTable.getSelectionModel().setSelectionInterval(row, row);
        myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

      }
      myTable.requestFocus();
    }
  }

  protected ClassFilter createFilter(String pattern) {
    try {
      Long.parseLong(pattern);
      return super.createFilter(pattern);
    } catch (NumberFormatException e) {
      Messages.showMessageDialog(this, "Instance ID should be a numeric value of long type.", "Invalid Number Format", Messages.getErrorIcon());
      return null;
    }
  }
}
