package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.peer.PeerFactory;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

public class CompareWithSelectedRevisionAction extends ActionGroup{
  private static final DateFormat DATE_FORMAT = SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT);

  public void update(AnActionEvent e) {
    final VcsContext vcsContext = createVcsContext(e);
    AbstractShowDiffAction.updateDiffAction(e.getPresentation(), vcsContext);
  }

  private VcsContext createVcsContext(final AnActionEvent e) {
    return PeerFactory.getInstance().getVcsContextFactory().createOn(e);
  }

  public AnAction[] getChildren(AnActionEvent e) {
    final VcsContext vcsContext = createVcsContext(e);
    final VirtualFile file = vcsContext.getSelectedFiles()[0];
    final Project project = vcsContext.getProject();
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    final VcsHistoryProvider vcsHistoryProvider = vcs.getVcsHistoryProvider();
    final ArrayList<AnAction> actions = new ArrayList<AnAction>();

    try {
      final VcsHistorySession session = vcsHistoryProvider.createSessionFor(new FilePathImpl(file));
      final List<VcsFileRevision> revisions = session.getRevisionList();
      for (Iterator<VcsFileRevision> iterator = revisions.iterator(); iterator.hasNext();) {
        final VcsFileRevision vcsFileRevision = iterator.next();
        actions.add(new AbstractShowDiffAction() {
          protected VcsRevisionNumber getRevisionNumber(DiffProvider diffProvider, VirtualFile file) {
            return vcsFileRevision.getRevisionNumber();
          }

          protected void update(VcsContext vcsContext, Presentation presentation) {
            presentation.setText(vcsFileRevision.getRevisionNumber().asString() +" " + DATE_FORMAT.format(vcsFileRevision.getRevisionDate()) + " " + vcsFileRevision.getAuthor());
            super.update(vcsContext, presentation);
          }
        });
      }

    }
    catch (VcsException e1) {

    }
    return actions.toArray(new AnAction[actions.size()]);
  }
}
