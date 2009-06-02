package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class RollbackChangesDialog extends DialogWrapper {
  private final Project myProject;
  private final boolean myRefreshSynchronously;
  private final Runnable myAfterVcsRefreshInAwt;
  private final MultipleChangeListBrowser myBrowser;
  @Nullable private JCheckBox myDeleteLocallyAddedFiles;

  public static void rollbackChanges(final Project project, final Collection<Change> changes) {
    rollbackChanges(project, changes, true);
  }

  public static void rollbackChanges(final Project project, final Collection<Change> changes, boolean refreshSynchronously) {
    rollbackChanges(project, changes, refreshSynchronously, null);
  }

  public static void rollbackChanges(final Project project, final Collection<Change> changes, boolean refreshSynchronously,
                                     final Runnable afterVcsRefreshInAwt) {
    final ChangeListManagerEx manager = (ChangeListManagerEx) ChangeListManager.getInstance(project);

    if (changes.isEmpty()) {
      Messages.showWarningDialog(project, VcsBundle.message("commit.dialog.no.changes.detected.text"),
                                 VcsBundle.message("commit.dialog.no.changes.detected.title"));
      return;
    }

    final ArrayList<Change> validChanges = new ArrayList<Change>();
    final Set<LocalChangeList> lists = new THashSet<LocalChangeList>();
    lists.addAll(manager.getInvolvedListsFilterChanges(changes, validChanges));

    rollback(project, new ArrayList<LocalChangeList>(lists), validChanges, refreshSynchronously, afterVcsRefreshInAwt);
  }

  public static void rollback(final Project project,
                              final List<LocalChangeList> changeLists,
                              final List<Change> changes,
                              final boolean refreshSynchronously, final Runnable afterVcsRefreshInAwt) {
    new RollbackChangesDialog(project, changeLists, changes, refreshSynchronously, afterVcsRefreshInAwt).show();
  }

  public RollbackChangesDialog(final Project project,
                               List<LocalChangeList> changeLists,
                               final List<Change> changes,
                               final boolean refreshSynchronously, final Runnable afterVcsRefreshInAwt) {
    super(project, true);

    myProject = project;
    myRefreshSynchronously = refreshSynchronously;
    myAfterVcsRefreshInAwt = afterVcsRefreshInAwt;
    myBrowser = new MultipleChangeListBrowser(project, changeLists, changes, null, true, true, null, null);
    myBrowser.setToggleActionTitle("Include in rollback");

    setOKButtonText(VcsBundle.message("changes.action.rollback.text"));
    setTitle(VcsBundle.message("changes.action.rollback.title"));

    Set<AbstractVcs> affectedVcs = new HashSet<AbstractVcs>();
    for (Change c : changes) {
      final AbstractVcs vcs = ChangesUtil.getVcsForChange(c, project);
      if (vcs != null) {
        // vcs may be null if we have turned off VCS integration and are in process of refreshing
        affectedVcs.add(vcs);
      }
    }
    if (affectedVcs.size() == 1) {
      AbstractVcs vcs = (AbstractVcs)affectedVcs.toArray()[0];
      final RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
      if (rollbackEnvironment != null) {
        final String rollbackOperationName = rollbackEnvironment.getRollbackOperationName().replace(Character.toString(UIUtil.MNEMONIC), "");
        setTitle(VcsBundle.message("changes.action.rollback.custom.title", rollbackOperationName).replace("_", ""));
        setOKButtonText(rollbackOperationName);
      }
    }

    for (Change c : changes) {
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
    new RollbackWorker(myProject, myRefreshSynchronously).doRollback(myBrowser.getCurrentIncludedChanges(),
                                                                     myDeleteLocallyAddedFiles != null && myDeleteLocallyAddedFiles.isSelected(),
                                                                     myAfterVcsRefreshInAwt, null);
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

  public JComponent getPreferredFocusedComponent() {
    return myBrowser.getPrefferedFocusComponent();
  }

  protected String getDimensionServiceKey() {
    return "RollbackChangesDialog";
  }
}
