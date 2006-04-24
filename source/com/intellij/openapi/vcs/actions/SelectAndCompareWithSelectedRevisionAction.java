package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Sep 7, 2005
 * Time: 3:06:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class SelectAndCompareWithSelectedRevisionAction extends AbstractVcsAction{
  protected void actionPerformed(VcsContext vcsContext) {

    final VirtualFile file = vcsContext.getSelectedFiles()[0];
    final Project project = vcsContext.getProject();
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    RevisionSelector selector = vcs.getRevisionSelector();
    final DiffProvider diffProvider = vcs.getDiffProvider();

    if (selector != null) {
      final VcsRevisionNumber vcsRevisionNumber = selector.selectNumber(file);

      if (vcsRevisionNumber != null) {
        AbstractShowDiffAction.showDiff(diffProvider, vcsRevisionNumber, file, project);
      }
    }

  }

  

  protected void update(VcsContext vcsContext, Presentation presentation) {
    AbstractShowDiffAction.updateDiffAction(presentation, vcsContext);
  }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }
}
