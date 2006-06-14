package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserDialog;
import org.jetbrains.idea.svn.dialogs.ImportDialog;
import org.jetbrains.idea.svn.SvnVcs;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 08.07.2005
 * Time: 21:44:21
 * To change this template use File | Settings | File Templates.
 */
public class ImportToRepositoryAction extends AnAction {
  
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    ImportDialog dialog = new ImportDialog(project);
    dialog.show();
  }

  public void update(AnActionEvent e) {
    super.update(e);

    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    SvnVcs vcs = project != null ? SvnVcs.getInstance(project) : null;
    e.getPresentation().setEnabled(vcs != null);
  }
}
