// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

import static org.jetbrains.idea.svn.SvnBundle.messagePointer;

public class SvnEditCommitMessageFromFileHistoryAction extends DumbAwareAction {
  public SvnEditCommitMessageFromFileHistoryAction() {
    super(
      messagePointer("action.EditCommitMessage.text"),
      messagePointer("action.EditCommitMessage.description"),
      AllIcons.Actions.Edit
    );
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    final VcsKey vcsKey = e.getData(VcsDataKeys.VCS);
    if (vcsKey == null || !SvnVcs.getKey().equals(vcsKey)) return;
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    if (revision == null || revisionVirtualFile == null) return;
    final SvnFileRevision svnFileRevision = (SvnFileRevision)revision;
    final Consumer<String> listener = e.getData(VcsDataKeys.REMOTE_HISTORY_CHANGED_LISTENER);
    SvnEditCommitMessageAction.askAndEditRevision(svnFileRevision.getRevision().getNumber(), svnFileRevision.getCommitMessage(),
                                                  svnFileRevision.getChangedRepositoryPath(), project,
                                                  newMessage -> {
                                                    svnFileRevision.setCommitMessage(newMessage);
                                                    if (listener != null) {
                                                      listener.consume(newMessage);
                                                    }
                                                    ProjectLevelVcsManager.getInstance(project).getVcsHistoryCache()
                                                      .editCached(VcsUtil.getFilePath(revisionVirtualFile), vcsKey, revisions -> {
                                                        for (VcsFileRevision fileRevision : revisions) {
                                                          if (!(fileRevision instanceof SvnFileRevision)) continue;
                                                          if (((SvnFileRevision)fileRevision).getRevision().getNumber() ==
                                                              svnFileRevision.getRevision().getNumber()) {
                                                            ((SvnFileRevision)fileRevision).setCommitMessage(newMessage);
                                                            break;
                                                          }
                                                        }
                                                      });
                                                  }, true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final VcsKey vcsKey = e.getData(VcsDataKeys.VCS);
    final boolean isSvn = SvnVcs.getKey().equals(vcsKey);
    e.getPresentation().setVisible(isSvn);
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    e.getPresentation().setEnabled(isSvn && revision != null);
  }
}
