// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static com.intellij.openapi.actionSystem.Presentation.NULL_STRING;
import static org.jetbrains.idea.svn.SvnBundle.messagePointer;

public class MergeSourceDetailsAction extends AnAction implements DumbAware {

  public MergeSourceDetailsAction() {
    super(messagePointer("action.Subversion.ShowMergeSourceDetails.text"), NULL_STRING, AllIcons.Vcs.Branch);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(enabled(e));
  }

  public void registerSelf(final JComponent comp) {
    registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.ALT_MASK | InputEvent.CTRL_MASK)), comp);
  }

  private boolean enabled(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return false;
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    if (revisionVirtualFile == null) return false;
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (revision == null) return false;
    if (! (revision instanceof SvnFileRevision)) return false;
    return ! ((SvnFileRevision) revision).getMergeSources().isEmpty();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (! enabled(e)) return;

    final Project project = e.getData(CommonDataKeys.PROJECT);
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    SvnMergeSourceDetails.showMe(project, (SvnFileRevision) revision, revisionVirtualFile);
  }
}
