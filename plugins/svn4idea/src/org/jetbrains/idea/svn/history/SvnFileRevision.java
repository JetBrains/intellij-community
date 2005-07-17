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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

public class SvnFileRevision implements VcsFileRevision {
  private byte[] myContent;
  private Date myDate;
  private String myCommitMessage;
  private String myAuthor;
  private VcsRevisionNumber myRevisionNumber;
  private SvnVcs myVCS;
  private String myURL;
  private SVNRevision myPegRevision;
  private SVNRevision myRevision;

  public SvnFileRevision(SvnVcs vcs,
                         SVNRevision pegRevision,
                         SVNRevision revision,
                         String url,
                         String author,
                         Date date,
                         String commitMessage) {
    myRevisionNumber = new SvnRevisionNumber(revision);
    myPegRevision = pegRevision;
    myRevision = revision;
    myAuthor = author;
    myDate = date;
    myCommitMessage = commitMessage;
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
    ConentLoader loader = new ConentLoader(myURL, contents, myRevision, myPegRevision);
    if (ApplicationManager.getApplication().isDispatchThread() &&
        !myRevision.isLocal()) {
      ApplicationManager.getApplication().runProcessWithProgressSynchronously(loader,
                                                                              "Loading Remote File Content", false, null);
    }
    else {
      loader.run();
    }
    if (loader.getException() == null) {
      myContent = contents.toByteArray();
    }
    else {
      myContent = new byte[0];
    }
  }

  public byte[] getContent() throws IOException {
    return myContent;
  }

  private class ConentLoader implements Runnable {
    private SVNRevision myRevision;
    private SVNRevision myPegRevision;
    private String myURL;
    private OutputStream myDst;
    private SVNException myException;

    public ConentLoader(String url, OutputStream dst, SVNRevision revision, SVNRevision pegRevision) {
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
        progress.setText("Loading contents of '" + myURL + "'");
        progress.setText2("Revision " + myRevision);
      }
      try {
        SVNWCClient client = myVCS.createWCClient();
        client.doGetFileContents(myURL, myPegRevision, myRevision, true, myDst);
      }
      catch (SVNException e) {
        myException = e;
      }
    }
  }
}
