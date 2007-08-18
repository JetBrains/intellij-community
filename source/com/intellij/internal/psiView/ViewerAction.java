/**
 * class ViewerAction
 * created Aug 27, 2001
 * @author Jeka
 */
package com.intellij.internal.psiView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;

public class ViewerAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = DataKeys.PROJECT.getData(e.getDataContext());
    PsiViewerDialog dialog = new PsiViewerDialog(project,false);
    dialog.show();
  }
}
