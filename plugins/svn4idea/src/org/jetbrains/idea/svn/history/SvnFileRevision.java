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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

public class SvnFileRevision implements VcsFileRevision {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history.SvnFileRevision");

  private byte[] myContent;
  private Date myDate;
  private String myCommitMessage;
  private String myAuthor;
  private VcsRevisionNumber myRevisionNumber;
  private SvnVcs myVCS;
  private String myURL;
  private SVNRevision myPegRevision;
  private SVNRevision myRevision;
  private String myCopyFromPath;

  public SvnFileRevision(SvnVcs vcs,
                         SVNRevision pegRevision,
                         SVNRevision revision,
                         String url,
                         String author,
                         Date date,
                         String commitMessage,
                         String copyFromPath) {
    myRevisionNumber = new SvnRevisionNumber(revision);
    myPegRevision = pegRevision;
    myRevision = revision;
    myAuthor = author;
    myDate = date;
    myCommitMessage = commitMessage;
    myCopyFromPath = copyFromPath;
    myVCS = vcs;
    myURL = url;
  }

  public SvnFileRevision(SvnVcs vcs,
                         SVNRevision pegRevision,
                         SVNLogEntry logEntry,
                         String url,
                         String copyFromPath) {
    final SVNRevision revision = SVNRevision.create(logEntry.getRevision());
    myRevisionNumber = new SvnRevisionNumber(revision);
    myPegRevision = pegRevision;
    myRevision = revision;
    myAuthor = logEntry.getAuthor();
    myDate = logEntry.getDate();
    myCommitMessage = logEntry.getMessage();
    myCopyFromPath = copyFromPath;
    myVCS = vcs;
    myURL = url;
  }

  public String getURL() {
    return myURL;
  }

  public SVNRevision getPegRevision() {
    return myPegRevision;
  }


  public VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  public String getBranchName() {
    return null;
  }

  public Date getRevisionDate() {
    return myDate;
  }

  public String getAuthor() {
    return myAuthor;
  }

  public String getCommitMessage() {
    return myCommitMessage;
  }

  public void loadContent() throws VcsException {
    ByteArrayOutputStream contents = new ByteArrayOutputStream();
    ContentLoader loader = new ContentLoader(myURL, contents, myRevision, myPegRevision);
    if (ApplicationManager.getApplication().isDispatchThread() &&
        !myRevision.isLocal()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(loader, SvnBundle.message("progress.title.loading.file.content"), false, myVCS.getProject());
    }
    else {
      loader.run();
    }
    if (loader.getException() == null) {
      myContent = contents.toByteArray();
    }
    else {
      final SVNException svnException = loader.getException();
      LOG.info("Failed to load file '" + myURL + "' content at revision: " + myRevision + "\n" + svnException.getMessage(), svnException);
      throw new VcsException(svnException);
    }
  }

  public byte[] getContent() throws IOException {
    return myContent;
  }

  public String getCopyFromPath() {
    return myCopyFromPath;
  }

  private class ContentLoader implements Runnable {
    private SVNRevision myRevision;
    private SVNRevision myPegRevision;
    private String myURL;
    private OutputStream myDst;
    private SVNException myException;

    public ContentLoader(String url, OutputStream dst, SVNRevision revision, SVNRevision pegRevision) {
      myURL = url;
      myDst = dst;
      myRevision = revision;
      myPegRevision = pegRevision;
    }

    public SVNException getException() {
      return myException;
    }

    public void run() {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.loading.contents", myURL));
        progress.setText2(SvnBundle.message("progress.text2.revision.information", myRevision));
      }
      try {
        SVNWCClient client = myVCS.createWCClient();
        client.doGetFileContents(SVNURL.parseURIEncoded(myURL), myPegRevision, myRevision, true, myDst);
      }
      catch (SVNException e) {
        myException = e;
      }
    }
  }
}
