/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnFileRevision;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

public class ShowAllSubmittedFilesAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.actions.ShowAllSubmittedFilesAction");

  public ShowAllSubmittedFilesAction() {
    super(SvnBundle.message("action.name.show.all.paths.affected"), null, IconLoader.getIcon("/icons/allRevisions.png"));
  }

  public void update(AnActionEvent e) {
    super.update(e);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    e.getPresentation().setEnabled((e.getData(VcsDataKeys.VCS_FILE_REVISION) != null) && (revisionVirtualFile != null));
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    if ((revision != null) && (revisionVirtualFile != null)) {
      final SvnFileRevision svnRevision = ((SvnFileRevision)revision);

      showSubmittedFiles(project, svnRevision, revisionVirtualFile);
    }
  }

  public static void showSubmittedFiles(final Project project, final SvnFileRevision svnRevision, final VirtualFile file) {
    final SvnChangeList changeList = loadRevisions(project, svnRevision, file);

    if (changeList != null) {
      long revNumber = ((SvnRevisionNumber)svnRevision.getRevisionNumber()).getRevision().getNumber();
      AbstractVcsHelper.getInstance(project).showChangesListBrowser(changeList, getTitle(revNumber));
    }
  }

  private static String getTitle(long revisionNumber) {
    return SvnBundle.message("dialog.title.affected.paths", revisionNumber);
  }

  @Nullable
  public static SvnChangeList loadRevisions(final Project project, final SvnFileRevision svnRevision, @Nullable final VirtualFile file) {
    return loadRevisions(project, svnRevision, file, true);
  }

  @Nullable
  public static SvnChangeList loadRevisions(final Project project, final SvnFileRevision svnRevision, @Nullable final VirtualFile file, boolean underProgress) {
    final Ref<SvnChangeList> result = new Ref<SvnChangeList>();
    final SvnRevisionNumber number = ((SvnRevisionNumber)svnRevision.getRevisionNumber());

    final SVNRevision targetRevision = ((SvnRevisionNumber)svnRevision.getRevisionNumber()).getRevision();
    final SvnVcs vcs = SvnVcs.getInstance(project);

    try {
      final Exception[] ex = new Exception[1];
      final String url = svnRevision.getURL();
      final SVNLogEntry[] logEntry = new SVNLogEntry[1];
      final SvnRepositoryLocation location = new SvnRepositoryLocation(url);

      final SVNLogClient client = vcs.createLogClient();
      final SVNURL repositoryUrl;
      if ((file != null) && file.isInLocalFileSystem()) {
        final SvnFileUrlMapping urlMapping = vcs.getSvnFileUrlMapping();
        final RootUrlInfo wcRoot = urlMapping.getWcRootForFilePath(new File(file.getPath()));
        if (wcRoot == null) {
          return null;
        }
        repositoryUrl = wcRoot.getRepositoryUrlUrl();
      } else {
        final SVNInfo svnInfo = vcs.createWCClient().doInfo(SVNURL.parseURIEncoded(url), SVNRevision.HEAD, SVNRevision.HEAD);
        repositoryUrl = svnInfo.getRepositoryRootURL();
        if (repositoryUrl == null) {
          Messages.showErrorDialog(SvnBundle.message("message.text.cannot.load.version", number, "Cannot get repository url"),
                                   SvnBundle.message("message.title.error.fetching.affected.paths"));
          return null;
        }
      }

      final Runnable process = new Runnable() {
        public void run() {
          try {

            ProgressManager.getInstance().getProgressIndicator().setText(SvnBundle.message("progress.text.loading.log"));
            client.doLog(repositoryUrl, null, targetRevision, targetRevision, targetRevision, false, true, 0, new ISVNLogEntryHandler() {
              public void handleLogEntry(final SVNLogEntry currentLogEntry) throws SVNException {
                logEntry[0] = currentLogEntry;
              }
            });
            if (logEntry[0] == null) {
              throw new VcsException(SvnBundle.message("exception.text.cannot.load.version", number));
            }

            ProgressManager.getInstance().getProgressIndicator().setText(SvnBundle.message("progress.text.processing.changes"));
            result.set(new SvnChangeList(vcs, location, logEntry[0], repositoryUrl.toString()));
          }
          catch (Exception e) {
            ex[0] = e;
          }
        }
      };
      if (underProgress) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(process, getTitle(targetRevision.getNumber()), false, project);
      } else {
        process.run();
      }
      if (ex[0] != null) throw ex[0];
    }
    catch (Exception e1) {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      e1.printStackTrace(new PrintStream(baos));
      LOG.info("For url: " + svnRevision.getURL() + "Exception: " + new String(baos.toByteArray()));

      Messages.showErrorDialog(SvnBundle.message("message.text.cannot.load.version", number, e1.getLocalizedMessage()),
                               SvnBundle.message("message.title.error.fetching.affected.paths"));
      return null;
    }

    return result.get();
  }
}
