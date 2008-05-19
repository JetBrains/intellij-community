package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.BranchConfigurationDialog;
import org.jetbrains.idea.svn.integrate.SvnChangeListHelper;

public class ConfigureBranchesAction extends AnAction {
  public void update(final AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final Presentation presentation = e.getPresentation();

    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    presentation.setText(SvnBundle.message("configure.branches.item"));
    presentation.setDescription(SvnBundle.message("configure.branches.item"));

    presentation.setVisible(true);
    
    final ChangeList[] cls = e.getData(VcsDataKeys.CHANGE_LISTS);
    presentation.setEnabled((cls != null) && (cls.length > 0) &&
                            (SvnVcs.getInstance(project).getName().equals(((CommittedChangeList) cls[0]).getVcs().getName())));
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final ChangeList[] cls = e.getData(VcsDataKeys.CHANGE_LISTS);
    if ((cls == null) || (cls.length == 0) ||
        (! SvnVcs.getInstance(project).getName().equals(((CommittedChangeList) cls[0]).getVcs().getName()))) {
      return;
    }
    final VirtualFile vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(
        SvnChangeListHelper.getAnyFileUnderChangeList((CommittedChangeList) cls[0]));
    BranchConfigurationDialog.configureBranches(project, vcsRoot);
  }
}
