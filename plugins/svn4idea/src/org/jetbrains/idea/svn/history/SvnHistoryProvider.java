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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.ShowAllSubmittedFilesAction;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SvnHistoryProvider implements VcsHistoryProvider {

  private final SvnVcs myVcs;
  private SVNURL myURL;
  private SVNRevision myRevision;

  public SvnHistoryProvider(SvnVcs vcs) {
    this(vcs, null, null);
  }

  public SvnHistoryProvider(SvnVcs vcs, SVNURL url, SVNRevision revision) {
    myVcs = vcs;
    myURL = url;
    myRevision = revision;
  }

  public HistoryAsTreeProvider getTreeHistoryProvider() {
    return null;
  }

  public ColumnInfo[] getRevisionColumns() {
    return new ColumnInfo[0];
  }

  public VcsHistorySession createSessionFor(final FilePath filePath) throws VcsException {
    return new VcsHistorySession(getRevisionsList(filePath)) {
      @Nullable
      public VcsRevisionNumber calcCurrentRevisionNumber() {
        return getCurrentRevision(filePath);
      }
    };
  }

  public List<VcsFileRevision> getRevisionsList(final FilePath file) throws VcsException {
    final SVNException[] exception = new SVNException[1];
    final ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();

    Runnable command = new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setText(SvnBundle.message("progress.text2.collecting.history", file.getName()));
        }
        try {
          if (myURL == null) {
            collectLogEntries(indicator, file, exception, result);
          } else {
            collectLogEntries2(indicator, result);
          }
        }
        catch (SVNException e) {
          exception[0] = e;
        }

      }
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.title.revisions.history"), false, myVcs.getProject());
    }
    else {
      command.run();
    }

    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
    return result;
  }

  private void collectLogEntries(final ProgressIndicator indicator, FilePath file, SVNException[] exception, final ArrayList<VcsFileRevision> result) throws SVNException {
    SVNWCClient wcClient = myVcs.createWCClient();
    SVNInfo info = wcClient.doInfo(new File(file.getIOFile().getAbsolutePath()), SVNRevision.WORKING);
    if (info == null) {
        exception[0] = new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "File ''{0}'' is not under version control", file.getIOFile()));
        return;
    }
    final String url = info.getURL() == null ? null : info.getURL().toString();
    if (indicator != null) {
      indicator.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", url));
    }
    final SVNRevision pegRevision = info.getRevision();
    SVNLogClient client = myVcs.createLogClient();
    client.doLog(new File[]{new File(file.getIOFile().getAbsolutePath())}, SVNRevision.HEAD, SVNRevision.create(1), false, false, 0,
                 new ISVNLogEntryHandler() {
                   public void handleLogEntry(SVNLogEntry logEntry) {
                     if (indicator != null) {
                       indicator.setText2(SvnBundle.message("progress.text2.revision.processed", logEntry.getRevision()));
                     }
                     Date date = logEntry.getDate();
                     String author = logEntry.getAuthor();
                     String message = logEntry.getMessage();
                     SVNRevision rev = SVNRevision.create(logEntry.getRevision());
                     result.add(new SvnFileRevision(myVcs, pegRevision, rev, url, author, date, message));
                   }
                 });
  }

  private void collectLogEntries2(final ProgressIndicator indicator, final ArrayList<VcsFileRevision> result) throws SVNException {
    if (indicator != null) {
      indicator.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", myURL.toString()));
    }
    SVNLogClient client = myVcs.createLogClient();
    client.doLog(myURL, new String[] {}, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNRevision.create(1), false, false, 0,
                 new ISVNLogEntryHandler() {
                   public void handleLogEntry(SVNLogEntry logEntry) {
                     if (indicator != null) {
                       indicator.setText2(SvnBundle.message("progress.text2.revision.processed", logEntry.getRevision()));
                     }
                     Date date = logEntry.getDate();
                     String author = logEntry.getAuthor();
                     String message = logEntry.getMessage();
                     SVNRevision rev = SVNRevision.create(logEntry.getRevision());
                     result.add(new SvnFileRevision(myVcs, SVNRevision.UNDEFINED, rev, myURL.toString(), author, date, message));
                   }
                 });
  }

  public String getHelpId() {
    return null;
  }

  public VcsRevisionNumber getCurrentRevision(FilePath file) {
    if (myRevision != null) {
      return new SvnRevisionNumber(myRevision);
    }
    try {
      SVNWCClient wcClient = myVcs.createWCClient();
      SVNInfo info = wcClient.doInfo(new File(file.getPath()).getAbsoluteFile(), SVNRevision.WORKING);
      if (info != null) {
        return new SvnRevisionNumber(info.getCommittedRevision());
      } else {
        return null;
      }
    }
    catch (SVNException e) {
      return null;
    }
  }

  public AnAction[] getAdditionalActions(final FileHistoryPanel panel) {
    return new AnAction[]{new ShowAllSubmittedFilesAction()};
  }
}
