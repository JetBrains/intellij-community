package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;

public class ShowPropertiesDiffWithLocalAction extends AbstractShowPropertiesDiffAction {
  private final Icon myIcon;

  public ShowPropertiesDiffWithLocalAction() {
    myIcon = IconLoader.getIcon("/icons/PropertiesDiffWithLocal.png");
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);

    e.getPresentation().setText(SvnBundle.message("action.Subversion.properties.diff.with.local.name"));
    e.getPresentation().setIcon(myIcon);
  }

  protected DataKey<Change[]> getChangesKey() {
    return VcsDataKeys.CHANGE_LEAD_SELECTION;
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
