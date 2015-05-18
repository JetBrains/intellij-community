/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.history;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.idea.svn.SvnVcs;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/25/12
 * Time: 10:22 AM
 */
public class SvnEditCommitMessageFromFileHistoryAction extends AnAction {
  public SvnEditCommitMessageFromFileHistoryAction() {
    super("Edit Revision Comment", "Edit revision comment. Previous message is rewritten.", AllIcons.Actions.Edit);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    final VcsKey vcsKey = e.getData(VcsDataKeys.VCS);
    if (vcsKey == null || ! SvnVcs.getKey().equals(vcsKey)) return;
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    if (revision == null || revisionVirtualFile == null) return;
    final SvnFileRevision svnFileRevision = (SvnFileRevision) revision;
    final Consumer<String> listener = VcsDataKeys.REMOTE_HISTORY_CHANGED_LISTENER.getData(e.getDataContext());
    SvnEditCommitMessageAction.askAndEditRevision(svnFileRevision.getRevision().getNumber(), svnFileRevision.getCommitMessage(),
      (SvnRepositoryLocation) svnFileRevision.getChangedRepositoryPath(), project, new Consumer<String>() {
      @Override
      public void consume(final String newMessage) {
        svnFileRevision.setCommitMessage(newMessage);
        if (listener != null) {
          listener.consume(newMessage);
        }
        ProjectLevelVcsManager.getInstance(project).getVcsHistoryCache().editCached(VcsUtil.getFilePath(revisionVirtualFile), vcsKey,
          new Consumer<List<VcsFileRevision>>() {
            @Override
            public void consume(List<VcsFileRevision> revisions) {
              for (VcsFileRevision fileRevision : revisions) {
                if (! (fileRevision instanceof SvnFileRevision)) continue;
                if (((SvnFileRevision) fileRevision).getRevision().getNumber() == svnFileRevision.getRevision().getNumber()) {
                  ((SvnFileRevision) fileRevision).setCommitMessage(newMessage);
                  break;
                }
              }
            }
          });
      }
    }, true);
  }

  @Override
  public void update(AnActionEvent e) {
    final VcsKey vcsKey = e.getData(VcsDataKeys.VCS);
    final boolean isSvn = SvnVcs.getKey().equals(vcsKey);
    e.getPresentation().setVisible(isSvn);
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    e.getPresentation().setEnabled(isSvn && revision != null);
  }
}
