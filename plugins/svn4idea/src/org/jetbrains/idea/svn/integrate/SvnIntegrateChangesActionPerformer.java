package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnBranchConfiguration;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.SelectBranchPopup;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.List;

public class SvnIntegrateChangesActionPerformer implements SelectBranchPopup.BranchSelectedCallback {
  private final Project myProject;
  private final List<CommittedChangeList> myChangeLists;
  private final SvnVcs myVcs;
  private final MergerFactory myMergerFactory;

  private SVNURL myCurrentBranch;

  public SvnIntegrateChangesActionPerformer(final Project project, final List<CommittedChangeList> changeLists,
                                            final MergerFactory mergerFactory) {
    myProject = project;
    myVcs = SvnVcs.getInstance(myProject);
    myChangeLists = changeLists;
    myMergerFactory = mergerFactory;
  }

  public void firstStep() {
    // not empty already checked before
    final CommittedChangeList firstList = myChangeLists.get(0);
    final FilePath anyFile = SvnChangeListHelper.getAnyFileUnderChangeList(firstList);
    myCurrentBranch = ((SvnChangeList) firstList).getBranchUrl();

    final VirtualFile vcsRoot = ProjectLevelVcsManager.getInstance(myProject).getVcsRootFor(anyFile);
    SelectBranchPopup.showForVCSRoot(myProject, vcsRoot, this, SvnBundle.message("action.Subversion.integrate.changes.select.branch.text"));
  }

  public void branchSelected(final Project project, final SvnBranchConfiguration configuration, final String url, final long revision) {
    if (myCurrentBranch.toString().equals(url)) {
      Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.source.and.target.same.text"), 
                               SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
      return;
    }

    final IntegratedSelectedOptionsDialog dialog = new IntegratedSelectedOptionsDialog(project, myCurrentBranch, url);
    dialog.show();

    if (dialog.isOK()) {
      ApplicationManager.getApplication().saveAll();
      dialog.saveOptions();

      final WorkingCopyInfo info = dialog.getSelectedWc();
      final File file = new File(info.getLocalPath());
      if ((! file.exists()) || (! file.isDirectory())) {
        Messages.showErrorDialog(SvnBundle.message("action.Subversion.integrate.changes.error.target.not.dir.text"),
                                 SvnBundle.message("action.Subversion.integrate.changes.messages.title"));
        return;
      }

      final SvnIntegrateChangesTask task = new SvnIntegrateChangesTask(myProject, myVcs, dialog.getSelectedWc(),
                                                                       myMergerFactory, myCurrentBranch);
      ProgressManager.getInstance().run(task);
    }
  }
}
