/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.FileHistoryPanel;
import com.intellij.openapi.vcs.history.HistoryAsTreeProvider;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.ShowAllSubmittedFilesAction;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
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

  public SvnHistoryProvider(SvnVcs vcs) {
    myVcs = vcs;
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
          indicator.setText("Collecting revisions history for '" + file.getName() + "'");
        }
        try {
          SVNWCClient wcClient = myVcs.createWCClient();
          SVNInfo info = wcClient.doInfo(new File(file.getIOFile().getAbsolutePath()), SVNRevision.WORKING);
          final String url = info.getURL();
          if (indicator != null) {
            indicator.setText2("Establishing connection to '" + url + "'");
          }
          final SVNRevision pegRevision = info.getRevision();
          SVNLogClient client = myVcs.createLogClient();
          client.doLog(new File[]{new File(file.getIOFile().getAbsolutePath())}, SVNRevision.HEAD, SVNRevision.create(1), false, false, 0,
                       new ISVNLogEntryHandler() {
                         public void handleLogEntry(SVNLogEntry logEntry) {
                           if (indicator != null) {
                             indicator.setText2("Revision '" + logEntry.getRevision() + " processed");
                           }
                           Date date = logEntry.getDate();
                           String author = logEntry.getAuthor();
                           String message = logEntry.getMessage();
                           SVNRevision rev = SVNRevision.create(logEntry.getRevision());
                           result.add(new SvnFileRevision(myVcs, pegRevision, rev, url, author, date, message));
                         }
                       });
        }
        catch (SVNException e) {
          exception[0] = e;
        }

      }
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().runProcessWithProgressSynchronously(command, "Revisions History", false, myVcs.getProject());
    }
    else {
      command.run();
    }

    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
    return result;
  }

  public String getHelpId() {
    return null;
  }

  public VcsRevisionNumber getCurrentRevision(FilePath file) {
    try {
      SVNWCClient wcClient = myVcs.createWCClient();
      SVNInfo info = wcClient.doInfo(new File(file.getPath()).getAbsoluteFile(), SVNRevision.WORKING);
      return new SvnRevisionNumber(info.getCommittedRevision());
    }
    catch (SVNException e) {
      return null;
    }
  }

  public AnAction[] getAdditionalActions(final FileHistoryPanel panel) {
    return new AnAction[]{new ShowAllSubmittedFilesAction()};
  }
}
