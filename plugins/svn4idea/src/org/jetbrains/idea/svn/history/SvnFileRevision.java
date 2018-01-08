// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.checkin.CommitInfo;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static com.intellij.openapi.progress.ProgressManager.progress;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.vcsUtil.VcsUtil.getFilePathOnNonLocal;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.getFileContents;
import static org.jetbrains.idea.svn.SvnUtil.parseUrl;

public class SvnFileRevision implements VcsFileRevision {
  private final static Logger LOG = Logger.getInstance(SvnFileRevision.class);

  private final Date myDate;
  private String myCommitMessage;
  private final String myAuthor;
  @NotNull private final SvnRevisionNumber myRevisionNumber;
  @NotNull private final SvnVcs myVCS;
  @NotNull private final Url myURL;
  private final Revision myPegRevision;
  private final String myCopyFromPath;
  @NotNull private final List<SvnFileRevision> myMergeSources = newArrayList();

  @Deprecated // Required for compatibility with external plugins.
  public SvnFileRevision(@NotNull SvnVcs vcs,
                         Revision pegRevision,
                         @NotNull Revision revision,
                         @NotNull String url,
                         String author,
                         Date date,
                         String commitMessage,
                         String copyFromPath) {
    this(vcs, pegRevision, revision, parseUrl(url, false), author, date, commitMessage, copyFromPath);
  }

  public SvnFileRevision(@NotNull SvnVcs vcs,
                         Revision pegRevision,
                         @NotNull Revision revision,
                         @NotNull Url url,
                         String author,
                         Date date,
                         String commitMessage,
                         String copyFromPath) {
    myRevisionNumber = new SvnRevisionNumber(revision);
    myPegRevision = pegRevision;
    myAuthor = author;
    myDate = date;
    myCommitMessage = commitMessage;
    myCopyFromPath = copyFromPath;
    myVCS = vcs;
    myURL = url;
  }

  public SvnFileRevision(@NotNull SvnVcs vcs,
                         Revision pegRevision,
                         LogEntry logEntry,
                         @NotNull Url url,
                         String copyFromPath) {
    myRevisionNumber = new SvnRevisionNumber(Revision.of(logEntry.getRevision()));
    myPegRevision = pegRevision;
    myAuthor = logEntry.getAuthor();
    myDate = logEntry.getDate();
    myCommitMessage = logEntry.getMessage();
    myCopyFromPath = copyFromPath;
    myVCS = vcs;
    myURL = url;
  }

  @NotNull
  public CommitInfo getCommitInfo() {
    return new CommitInfo.Builder(myRevisionNumber.getRevision().getNumber(), myDate, myAuthor).build();
  }

  @NotNull
  public Url getURL() {
    return myURL;
  }

  public Revision getPegRevision() {
    return myPegRevision;
  }

  @NotNull
  public SvnRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  public String getBranchName() {
    return null;
  }

  @Nullable
  @Override
  public SvnRepositoryLocation getChangedRepositoryPath() {
    return new SvnRepositoryLocation(myURL.toString());
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

  public void addMergeSource(@NotNull SvnFileRevision source) {
    myMergeSources.add(source);
  }

  @NotNull
  public List<SvnFileRevision> getMergeSources() {
    return myMergeSources;
  }

  public byte[] loadContent() throws VcsException {
    ContentLoader loader = new ContentLoader();
    if (ApplicationManager.getApplication().isDispatchThread() && !getRevision().isLocal()) {
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(loader, message("progress.title.loading.file.content"), false, myVCS.getProject());
    }
    else {
      loader.run();
    }

    VcsException exception = loader.getException();
    if (exception == null) {
      final byte[] contents = loader.getContents();
      ContentRevisionCache.checkContentsSize(myURL.toDecodedString(), contents.length);
      return contents;
    }
    else {
      LOG
        .info("Failed to load file '" + myURL.toDecodedString() + "' content at revision: " + getRevision() + "\n" + exception.getMessage(),
              exception);
      throw exception;
    }
  }

  public byte[] getContent() throws IOException, VcsException {
    byte[] result;

    if (Revision.HEAD.equals(getRevision())) {
      result = loadContent();
    }
    else {
      result = ContentRevisionCache
        .getOrLoadAsBytes(myVCS.getProject(), getFilePathOnNonLocal(myURL.toDecodedString(), false), getRevisionNumber(),
                          myVCS.getKeyInstanceMethod(), ContentRevisionCache.UniqueType.REMOTE_CONTENT, () -> loadContent());
    }

    return result;
  }

  public String getCopyFromPath() {
    return myCopyFromPath;
  }

  public void setCommitMessage(String message) {
    myCommitMessage = message;
  }

  private class ContentLoader implements Runnable {
    private VcsException myException;
    private byte[] myContents;

    public VcsException getException() {
      return myException;
    }

    private byte[] getContents() {
      return myContents;
    }

    public void run() {
      progress(message("progress.text.loading.contents", myURL.toDecodedString()),
               message("progress.text2.revision.information", getRevision()));

      try {
        myContents = getFileContents(myVCS, Target.on(myURL), getRevision(), myPegRevision);
      }
      catch (VcsException e) {
        myException = e;
      }
    }
  }

  @NotNull
  public Revision getRevision() {
    return myRevisionNumber.getRevision();
  }
}
