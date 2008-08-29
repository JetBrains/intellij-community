package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.dialogs.SvnMapDialog;

public class ShowSvnMapAction extends AnAction {
  @Override
  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project) dataContext.getData(PlatformDataKeys.PROJECT.getName());

    final Presentation presentation = e.getPresentation();
    presentation.setVisible(project != null);
    presentation.setEnabled(project != null);

    presentation.setText(SvnBundle.message("action.show.svn.map.text"));
    presentation.setDescription(SvnBundle.message("action.show.svn.map.description"));
    presentation.setIcon(IconLoader.getIcon("/icons/ShowWorkingCopies.png"));
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project) dataContext.getData(PlatformDataKeys.PROJECT.getName());
    if (project == null) {
      return;
    }

    final SvnMapDialog dialog = new SvnMapDialog(project);
    dialog.show();
  }
}
