package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsDataConstants;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.versions.AbstractRevisions;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.checkin.AbstractSvnRevisionsFactory;
import org.jetbrains.idea.svn.history.SvnFileRevision;
import org.jetbrains.idea.svn.history.SvnVersionRevisions;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ShowAllSubmittedFilesAction extends AnAction {
  public ShowAllSubmittedFilesAction() {
    super("Show All Revisions Submitted In Selected Change List", null, IconLoader.findIcon("/icons/allRevisions.png"));
  }

  public void update(AnActionEvent e) {
    super.update(e);
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(e.getDataContext().getData(VcsDataConstants.VCS_FILE_REVISION) != null);
  }

  public void actionPerformed(AnActionEvent e) {

    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) return;
    final VcsFileRevision revision = (VcsFileRevision)e.getDataContext().getData(VcsDataConstants.VCS_FILE_REVISION);
    if (revision != null) {
      final SvnFileRevision svnRevision = ((SvnFileRevision)revision);

      final ArrayList<AbstractRevisions> revisions = loadRevisions(project, svnRevision);

      if (revisions != null) {
        long revNumber = ((SvnRevisionNumber)revision.getRevisionNumber()).getRevision().getNumber();
        AbstractVcsHelper.getInstance(project).showRevisions(revisions, getTitle(revNumber));
      }

    }
  }

  private static String getTitle(long revisionNumber) {
    return "Show All Revision Submitted in ChangeList " + revisionNumber;
  }

  private ArrayList<AbstractRevisions> loadRevisions(final Project project, final SvnFileRevision svnRevision) {
    final SvnRevisionNumber number = ((SvnRevisionNumber)svnRevision.getRevisionNumber());
    final ArrayList<AbstractRevisions> revisions = new ArrayList<AbstractRevisions>();

    final SVNRevision targetRevision = ((SvnRevisionNumber)svnRevision.getRevisionNumber()).getRevision();
    final SvnVcs vcs = SvnVcs.getInstance(project);

    try {
      final Exception[] ex = new Exception[1];
      final String url = svnRevision.getURL();
      final SVNLogEntry[] logEntry = new SVNLogEntry[1];
      final SVNRepository repos = vcs.createRepository(url);
      ApplicationManager.getApplication().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          try {
            ProgressManager.getInstance().getProgressIndicator().setText("Loading revision log...");
            repos.log(new String[]{"/"}, targetRevision.getNumber(), targetRevision.getNumber(), true, true, 0, new ISVNLogEntryHandler() {
              public void handleLogEntry(SVNLogEntry currentLogEntry) {
                logEntry[0] = currentLogEntry;
              }
            });
            if (logEntry[0] == null) {
              throw new VcsException("Cannot load repository version " + number);
            }

            ProgressManager.getInstance().getProgressIndicator().setText("Processing changes...");
            AbstractSvnRevisionsFactory<SVNLogEntryPath> factory = new EntryRevisionsFactory(vcs, logEntry[0], repos);
            revisions.addAll(factory.createRevisionsListOn(new String[]{File.separator}));
          }
          catch (Exception e) {
            ex[0] = e;
          }
        }
      }, getTitle(targetRevision.getNumber()), false, project);
      if (ex[0] != null) throw ex[0];
    }
    catch (Exception e1) {
      Messages.showErrorDialog("Cannot load repository version " + number + " :" + e1.getLocalizedMessage(),
                               getTitle(targetRevision.getNumber()));
      return null;
    }

    return revisions;
  }

  private static class EntryRevisionsFactory extends AbstractSvnRevisionsFactory<SVNLogEntryPath> {
    private SVNLogEntry myLogEntry;
    private SVNRepository myRepository;

    protected EntryRevisionsFactory(SvnVcs svnVcs, SVNLogEntry entry, SVNRepository repos) {
      super(svnVcs, entry);
      myLogEntry = entry;
      myRepository = repos;
    }

    public Map<File, SVNLogEntryPath> createFileToChangeMap(String[] paths) {
      Map changedPaths = myLogEntry.getChangedPaths();
      Map result = new HashMap();
      for (Iterator logPaths = changedPaths.keySet().iterator(); logPaths.hasNext();) {
        String logPath = (String)logPaths.next();
        result.put(new File(logPath), changedPaths.get(logPath));
      }
      return result;
    }

    protected String getPath(final SVNLogEntryPath svnStatus) {
      return svnStatus.getPath();
    }

    protected boolean shouldAddChange(final SVNLogEntryPath svnStatus) {
      return true;
    }

    protected AbstractRevisions createRevisions(final File file) {
      return new SvnVersionRevisions(myFileToTreeElementMap.get(file), this, myRepository, myLogEntry.getRevision());
    }
  }
}
