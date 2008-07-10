package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class ShowPropertiesDiffWithLocalAction extends AbstractShowPropertiesDiffAction {
  @Override
  public void update(final AnActionEvent e) {
    super.update(e);

    e.getPresentation().setText(SvnBundle.message("action.Subversion.properties.diff.with.local.name"));
  }

  protected DataKey<Change[]> getChangesKey() {
    // selected in this context
    return VcsDataKeys.CHANGES;
  }

  @Nullable
  protected SVNRevision getBeforeRevisionValue(final Change change, final SvnVcs vcs) throws SVNException {
    return SVNRevision.HEAD;
  }

  @Nullable
  protected SVNRevision getAfterRevisionValue(final Change change, final SvnVcs vcs) throws SVNException {
    return SVNRevision.WORKING;
  }
}
