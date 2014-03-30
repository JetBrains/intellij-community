/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SvnFileRevision implements VcsFileRevision {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history.SvnFileRevision");

  private final Date myDate;
  private String myCommitMessage;
  private final String myAuthor;
  private final VcsRevisionNumber myRevisionNumber;
  private final SvnVcs myVCS;
  private final String myURL;
  private final SVNRevision myPegRevision;
  private final SVNRevision myRevision;
  private final String myCopyFromPath;
  private final List<SvnFileRevision> myMergeSources;
  private final Charset myCharset;

  public SvnFileRevision(SvnVcs vcs,
                         SVNRevision pegRevision,
                         SVNRevision revision,
                         String url,
                         String author,
                         Date date,
                         String commitMessage,
                         String copyFromPath, Charset charset) {
    myCharset = charset;
    myRevisionNumber = new SvnRevisionNumber(revision);
    myPegRevision = pegRevision;
    myRevision = revision;
    myAuthor = author;
    myDate = date;
    myCommitMessage = commitMessage;
    myCopyFromPath = copyFromPath;
    myVCS = vcs;
    myURL = url;
    myMergeSources = new ArrayList<SvnFileRevision>();
  }

  public SvnFileRevision(SvnVcs vcs,
                         SVNRevision pegRevision,
                         SVNLogEntry logEntry,
                         String url,
                         String copyFromPath, Charset charset) {
    myCharset = charset;
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
    myMergeSources = new ArrayList<SvnFileRevision>();
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

  @Nullable
  @Override
  public RepositoryLocation getChangedRepositoryPath() {
    return new SvnRepositoryLocation(myURL);
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

  public void addMergeSource(final SvnFileRevision source) {
    myMergeSources.add(source);
  }

  public List<SvnFileRevision> getMergeSources() {
    return myMergeSources;
  }

  public byte[] loadContent() throws IOException, VcsException {
    ContentLoader loader = new ContentLoader(myURL, myRevision, myPegRevision);
    if (ApplicationManager.getApplication().isDispatchThread() &&
        !myRevision.isLocal()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(loader, SvnBundle.message("progress.title.loading.file.content"),
                                                                        false, myVCS.getProject());
    }
    else {
      loader.run();
    }
    if (loader.getException() == null) {
      final byte[] contents = loader.getContents();
      ContentRevisionCache.checkContentsSize(myURL, contents.length);
      return contents;
    }
    else {
      final VcsException vcsException = loader.getException();
      LOG.info("Failed to load file '" + myURL + "' content at revision: " + myRevision + "\n" + vcsException.getMessage(), vcsException);
      throw vcsException;
    }
  }

  public byte[] getContent() throws IOException, VcsException {
    return ContentRevisionCache.getOrLoadAsBytes(myVCS.getProject(), VcsContextFactory.SERVICE.getInstance()
                                                   .createFilePathOnNonLocal(myURL, false),
                                                 getRevisionNumber(), myVCS.getKeyInstanceMethod(), ContentRevisionCache.UniqueType.REMOTE_CONTENT,
                                                 new Throwable2Computable<byte[], VcsException, IOException>() {
                                                   @Override
                                                   public byte[] compute() throws VcsException, IOException {
                                                     byte[] bytes = loadContent();
                                                     return bytes;
                                                     //return SvnUtil.decode(myCharset, bytes);
                                                   }
                                                 });
  }

  public String getCopyFromPath() {
    return myCopyFromPath;
  }

  public void setCommitMessage(String message) {
    myCommitMessage = message;
  }

  private class ContentLoader implements Runnable {
    private final SVNRevision myRevision;
    private final SVNRevision myPegRevision;
    private final String myURL;
    private VcsException myException;
    private byte[] myContents;

    public ContentLoader(String url, SVNRevision revision, SVNRevision pegRevision) {
      myURL = url;
      myRevision = revision;
      myPegRevision = pegRevision;
    }

    public VcsException getException() {
      return myException;
    }

    private byte[] getContents() {
      return myContents;
    }

    public void run() {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.loading.contents", myURL));
        progress.setText2(SvnBundle.message("progress.text2.revision.information", myRevision));
      }

      try {
        myContents = SvnUtil.getFileContents(myVCS, SvnTarget.fromURL(SvnUtil.parseUrl(myURL)), myRevision, myPegRevision);
      }
      catch (VcsException e) {
        myException = e;
      }
    }
  }

  public SVNRevision getRevision() {
    return myRevision;
  }
}
