/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnFileRevision;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class ShowAllSubmittedFilesAction extends AnAction {
  public ShowAllSubmittedFilesAction() {
    super(SvnBundle.message("action.name.show.all.paths.affected"), null, IconLoader.findIcon("/icons/allRevisions.png"));
  }

  public void update(AnActionEvent e) {
    super.update(e);
    final Project project = e.getData(DataKeys.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(e.getData(VcsDataKeys.VCS_FILE_REVISION) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    if (project == null) return;
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (revision != null) {
      final SvnFileRevision svnRevision = ((SvnFileRevision)revision);

      final SvnChangeList changeList = loadRevisions(project, svnRevision);

      if (changeList != null) {
        long revNumber = ((SvnRevisionNumber)revision.getRevisionNumber()).getRevision().getNumber();
        AbstractVcsHelper.getInstance(project).showChangesBrowser(changeList, getTitle(revNumber));
      }
    }
  }

  private static String getTitle(long revisionNumber) {
    return SvnBundle.message("dialog.title.affected.paths", revisionNumber);
  }

  @Nullable
  private static SvnChangeList loadRevisions(final Project project, final SvnFileRevision svnRevision) {
    final Ref<SvnChangeList> result = new Ref<SvnChangeList>();
    final SvnRevisionNumber number = ((SvnRevisionNumber)svnRevision.getRevisionNumber());

    final SVNRevision targetRevision = ((SvnRevisionNumber)svnRevision.getRevisionNumber()).getRevision();
    final SvnVcs vcs = SvnVcs.getInstance(project);

    try {
      final Exception[] ex = new Exception[1];
      final String url = svnRevision.getURL();
      final SVNLogEntry[] logEntry = new SVNLogEntry[1];
      final SVNRepository repos = vcs.createRepository(url);
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          try {
            ProgressManager.getInstance().getProgressIndicator().setText(SvnBundle.message("progress.text.loading.log"));
            repos.log(new String[]{"/"}, targetRevision.getNumber(), targetRevision.getNumber(), true, true, 0, new ISVNLogEntryHandler() {
              public void handleLogEntry(SVNLogEntry currentLogEntry) {
                logEntry[0] = currentLogEntry;
              }
            });
            if (logEntry[0] == null) {
              throw new VcsException(SvnBundle.message("exception.text.cannot.load.version", number));
            }

            ProgressManager.getInstance().getProgressIndicator().setText(SvnBundle.message("progress.text.processing.changes"));
            result.set(new SvnChangeList(logEntry [0], repos));
          }
          catch (Exception e) {
            ex[0] = e;
          }
        }
      }, getTitle(targetRevision.getNumber()), false, project);
      if (ex[0] != null) throw ex[0];
    }
    catch (Exception e1) {
      Messages.showErrorDialog(SvnBundle.message("message.text.cannot.load.version", number, e1.getLocalizedMessage()),
                               SvnBundle.message("message.title.error.fetching.affected.paths"));
      return null;
    }

    return result.get();
  }
}
