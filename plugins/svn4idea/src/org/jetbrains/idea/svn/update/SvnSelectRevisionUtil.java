package org.jetbrains.idea.svn.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.ChangesBrowserDialog;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesTableModel;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;

public class SvnSelectRevisionUtil {
  private SvnSelectRevisionUtil() {
  }

  @Nullable
  public static SvnChangeList chooseCommittedChangeList(final Project project, final SvnRepositoryLocation location,
                                                              final VirtualFile root) {
    try {
      final SvnCommittedChangesTableModel model = new SvnCommittedChangesTableModel(location, project, root,
                                                  SvnVcs.getInstance(project).getCommittedChangesProvider().getColumns());
      final ChangesBrowserDialog dlg = new ChangesBrowserDialog(project, model, ChangesBrowserDialog.Mode.Choose);
      dlg.show();
      if (dlg.isOK()) {
        return (SvnChangeList) dlg.getSelectedChangeList();
      }
      model.onBeforeClose();
    }
    catch (VcsException e) {
      Messages.showErrorDialog(e.getMessage(), SvnBundle.message("error.cannot.load.revisions"));
    }
    return null;
  }
}
