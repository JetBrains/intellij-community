package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class MergeSourceDetailsAction extends AnAction implements DumbAware {
  private static Icon myIcon;

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setIcon(getIcon());
    e.getPresentation().setText("Show merge sources details");
    e.getPresentation().setEnabled(enabled(e));
  }

  private Icon getIcon() {
    if (myIcon == null) {
      myIcon = IconLoader.getIcon("/icons/mergeSourcesDetails.png");
    }
    return myIcon;
  }

  public void registerSelf(final JComponent comp) {
    registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.ALT_MASK | KeyEvent.CTRL_MASK)), comp);
  }

  private boolean enabled(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return false;
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    if (revisionVirtualFile == null) return false;
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (revision == null) return false;
    if (! (revision instanceof SvnFileRevision)) return false;
    return ! ((SvnFileRevision) revision).getMergeSources().isEmpty();
  }

  public void actionPerformed(AnActionEvent e) {
    if (! enabled(e)) return;

    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    SvnMergeSourceDetails.showMe(project, (SvnFileRevision) revision, revisionVirtualFile);
  }
}
