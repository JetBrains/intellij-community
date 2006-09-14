package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.awt.*;

/**
 * @author max
 */
public class RollbackChangesDialog extends DialogWrapper {
  private Project myProject;
  private final boolean myRefreshSynchronously;
  private ChangesBrowser myBrowser;
  @Nullable private JCheckBox myDeleteLocallyAddedFiles;

  public static void rollbackChanges(final Project project, final Collection<Change> changes) {
    rollbackChanges(project, changes, false);
  }

  public static void rollbackChanges(final Project project, final Collection<Change> changes, boolean refreshSynchronously) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);

    if (changes.isEmpty()) {
      Messages.showWarningDialog(project, VcsBundle.message("commit.dialog.no.changes.detected.text") ,
                                 VcsBundle.message("commit.dialog.no.changes.detected.title"));
      return;
    }

    ArrayList<Change> validChanges = new ArrayList<Change>();
    Set<LocalChangeList> lists = new THashSet<LocalChangeList>();
    for (Change change : changes) {
      final LocalChangeList list = manager.getChangeList(change);
      if (list != null) {
        lists.add(list);
        validChanges.add(change);
      }
    }

    rollback(project, new ArrayList<LocalChangeList>(lists), validChanges, refreshSynchronously);
  }

  public static void rollback(final Project project, final List<LocalChangeList> changeLists, final List<Change> changes,
                              final boolean refreshSynchronously) {
    new RollbackChangesDialog(project, changeLists, changes, refreshSynchronously).show();
  }

  public RollbackChangesDialog(final Project project, List<LocalChangeList> changeLists, final List<Change> changes,
                               final boolean refreshSynchronously) {
    super(project, true);

    myProject = project;
    myRefreshSynchronously = refreshSynchronously;
    myBrowser = new ChangesBrowser(project, changeLists, changes, null, true, true);

    setOKButtonText(VcsBundle.message("changes.action.rollback.text"));
    setTitle(VcsBundle.message("changes.action.rollback.title"));

    for(Change c: changes) {
      if (c.getType() == Change.Type.NEW) {
        myDeleteLocallyAddedFiles = new JCheckBox(VcsBundle.message("changes.checkbox.delete.locally.added.files"));
        break;
      }
    }

    init();
  }

  @Override
  protected void dispose() {
    super.dispose();
    myBrowser.dispose();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    doRollback();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    if (myDeleteLocallyAddedFiles != null) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(myBrowser, BorderLayout.CENTER);
      panel.add(myDeleteLocallyAddedFiles, BorderLayout.SOUTH);
      return panel;
    }
    return myBrowser;
  }

  private static List<FilePath> getFilePaths(Collection<Change> changes) {
    List<FilePath> paths = new ArrayList<FilePath>();
    for (Change change : changes) {
      paths.add(ChangesUtil.getFilePath(change));
    }
    return paths;
  }

  private void doRollback() {
    final List<VcsException> vcsExceptions = new ArrayList<VcsException>();
    final List<FilePath> pathsToRefresh = new ArrayList<FilePath>();

    Runnable rollbackAction = new Runnable() {
      public void run() {
        ChangesUtil.processChangesByVcs(myProject, myBrowser.getCurrentIncludedChanges(), new ChangesUtil.PerVcsProcessor<Change>() {
          public void process(AbstractVcs vcs, List<Change> changes) {
            final ChangeProvider environment = vcs.getChangeProvider();
            if (environment != null) {
              pathsToRefresh.addAll(getFilePaths(changes));

              final List<VcsException> exceptions = environment.rollbackChanges(changes);
              if (exceptions.size() > 0) {
                vcsExceptions.addAll(exceptions);
              }
              else if (myDeleteLocallyAddedFiles != null && myDeleteLocallyAddedFiles.isSelected()) {
                for(Change c: changes) {
                  if (c.getType() == Change.Type.NEW) {
                    ContentRevision rev = c.getAfterRevision();
                    assert rev != null;
                    FileUtil.delete(rev.getFile().getIOFile());
                  }
                }
              }
            }
          }
        });

        if (!myRefreshSynchronously) {
          doRefresh(pathsToRefresh, true);
        }
        AbstractVcsHelper.getInstance(myProject).showErrors(vcsExceptions, VcsBundle.message("changes.action.rollback.text"));
      }
    };

    ProgressManager.getInstance().runProcessWithProgressSynchronously(rollbackAction, VcsBundle.message("changes.action.rollback.text"), true, myProject);
    if (myRefreshSynchronously) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          doRefresh(pathsToRefresh, false);
        }
      });
    }
  }

  private void doRefresh(final List<FilePath> pathsToRefresh, final boolean asynchronous) {
    final LvcsAction lvcsAction = LocalVcs.getInstance(myProject).startAction(VcsBundle.message("changes.action.rollback.text"), "", true);
    VirtualFileManager.getInstance().refresh(asynchronous, new Runnable() {
      public void run() {
        lvcsAction.finish();
        FileStatusManager.getInstance(myProject).fileStatusesChanged();
        for (FilePath path : pathsToRefresh) {
          VcsDirtyScopeManager.getInstance(myProject).fileDirty(path);
        }
      }
    });
  }

  public JComponent getPreferredFocusedComponent() {
    return myBrowser.getPrefferedFocusComponent();
  }

  protected String getDimensionServiceKey() {
    return "RollbackCahgnesDialog";
  }
}
