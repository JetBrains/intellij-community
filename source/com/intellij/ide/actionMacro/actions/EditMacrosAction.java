package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.actionMacro.EditMacrosDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 22, 2003
 * Time: 3:33:04 PM
 * To change this template use Options | File Templates.
 */
public class EditMacrosAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    EditMacrosDialog dialog = new EditMacrosDialog((Project)e.getDataContext().getData(DataConstants.PROJECT));
    dialog.show();
  }
}
