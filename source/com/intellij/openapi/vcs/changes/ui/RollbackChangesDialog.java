package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFileManager;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class RollbackChangesDialog extends DialogWrapper {
  private Project myProject;
  private ChangesBrowser myBrowser;

  public static void rollbackChanges(final Project project, final Collection<Change> changes) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);

    if (changes.isEmpty()) {
      Messages.showWarningDialog(project, VcsBundle.message("commit.dialog.no.changes.detected.text") ,
                                 VcsBundle.message("commit.dialog.no.changes.detected.title"));
      return;
    }

    Set<LocalChangeList> lists = new THashSet<LocalChangeList>();
    for (Change change : changes) {
      lists.add(manager.getChangeList(change));
    }

    rollback(project, new ArrayList<LocalChangeList>(lists), new ArrayList<Change>(changes));
  }

  public static void rollback(final Project project, final List<LocalChangeList> changeLists, final List<Change> changes) {
    new RollbackChangesDialog(project, changeLists, changes).show();
  }

  public RollbackChangesDialog(final Project project, List<LocalChangeList> changeLists, final List<Change> changes) {
    super(project, true);

    myProject = project;
    myBrowser = new ChangesBrowser(project, changeLists, changes, null, true, true);


    setOKButtonText(VcsBundle.message("changes.action.rollback.text"));
    setTitle(VcsBundle.message("changes.action.rollback.title"));

    init();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    doRollback();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myBrowser;
  }

  private static List<FilePath> getFilePathes(Collection<Change> changes) {
    List<FilePath> paths = new ArrayList<FilePath>();
    for (Change change : changes) {
      paths.add(ChangesUtil.getFilePath(change));
    }
    return paths;
  }

  private void doRollback() {
    final List<VcsException> vcsExceptions = new ArrayList<VcsException>();

    Runnable rollbackAction = new Runnable() {
      public void run() {
        final List<FilePath> pathsToRefresh = new ArrayList<FilePath>();
        ChangesUtil.processChangesByVcs(myProject, myBrowser.getCurrentIncludedChanges(), new ChangesUtil.PerVcsProcessor<Change>() {
          public void process(AbstractVcs vcs, List<Change> changes) {
            final ChangeProvider environment = vcs.getChangeProvider();
            if (environment != null) {
              pathsToRefresh.addAll(getFilePathes(changes));

              final List<VcsException> exceptions = environment.rollbackChanges(changes);
              if (exceptions.size() > 0) {
                vcsExceptions.addAll(exceptions);
              }
            }
          }
        });

        final LvcsAction lvcsAction = LocalVcs.getInstance(myProject).startAction(VcsBundle.message("changes.action.rollback.text"), "", true);
        VirtualFileManager.getInstance().refresh(true, new Runnable() {
          public void run() {
            lvcsAction.finish();
            FileStatusManager.getInstance(myProject).fileStatusesChanged();
            for (FilePath path : pathsToRefresh) {
              VcsDirtyScopeManager.getInstance(myProject).fileDirty(path);
            }
          }
        });
        AbstractVcsHelper.getInstance(myProject).showErrors(vcsExceptions, VcsBundle.message("changes.action.rollback.text"));
      }
    };

    ProgressManager.getInstance().runProcessWithProgressSynchronously(rollbackAction, VcsBundle.message("changes.action.rollback.text"), true, myProject);
  }

  public JComponent getPreferredFocusedComponent() {
    return myBrowser.getPrefferedFocusComponent();
  }

  protected String getDimensionServiceKey() {
    return "RollbackCahgnesDialog";
  }
}
