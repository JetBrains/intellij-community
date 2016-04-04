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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.11.2006
 * Time: 17:48:18
 */
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.MarkerVcsContentRevision;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class SvnRepositoryContentRevision implements ByteBackedContentRevision, MarkerVcsContentRevision {

  @NotNull private final SvnVcs myVcs;
  @NotNull private final String myPath;
  @NotNull private final FilePath myFilePath;
  private final long myRevision;

  public SvnRepositoryContentRevision(@NotNull SvnVcs vcs, @NotNull FilePath remotePath, @Nullable FilePath localPath, long revision) {
    myVcs = vcs;
    myPath = FileUtil.toSystemIndependentName(remotePath.getPath());
    myFilePath = localPath != null ? localPath : remotePath;
    myRevision = revision;
  }

  @NotNull
  public String getContent() throws VcsException {
    return ContentRevisionCache.getAsString(getContentAsBytes(), myFilePath, null);
  }

  @NotNull
  @Override
  public byte[] getContentAsBytes() throws VcsException {
    try {
      if (myFilePath.getVirtualFile() == null) {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(myFilePath.getPath());
      }
      return ContentRevisionCache.getOrLoadAsBytes(myVcs.getProject(), myFilePath, getRevisionNumber(), myVcs.getKeyInstanceMethod(),
                                                   ContentRevisionCache.UniqueType.REPOSITORY_CONTENT,
                                                   new Throwable2Computable<byte[], VcsException, IOException>() {
                                                     @Override
                                                     public byte[] compute() throws VcsException, IOException {
                                                       return loadContent().toByteArray();
                                                     }
                                                   });
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  @NotNull
  protected ByteArrayOutputStream loadContent() throws VcsException {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    ContentLoader loader = new ContentLoader(myPath, buffer, myRevision);
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(loader, SvnBundle.message("progress.title.loading.file.content"), false, null);
    }
    else {
      loader.run();
    }
    final Exception exception = loader.getException();
    if (exception != null) {
      throw new VcsException(exception);
    }
    ContentRevisionCache.checkContentsSize(myPath, buffer.size());
    return buffer;
  }

  @NotNull
  public FilePath getFile() {
    return myFilePath;
  }

  @NotNull
  public SvnRevisionNumber getRevisionNumber() {
    return new SvnRevisionNumber(SVNRevision.create(myRevision));
  }

  public static SvnRepositoryContentRevision create(@NotNull SvnVcs vcs,
                                                    @NotNull String repositoryRoot,
                                                    @NotNull String path,
                                                    @Nullable FilePath localPath,
                                                    long revision) {
    return create(vcs, SvnUtil.appendMultiParts(repositoryRoot, path), localPath, revision);
  }

  public static SvnRepositoryContentRevision createForRemotePath(@NotNull SvnVcs vcs,
                                                                 @NotNull String repositoryRoot,
                                                                 @NotNull String path,
                                                                 boolean isDirectory,
                                                                 long revision) {
    FilePath remotePath = VcsUtil.getFilePathOnNonLocal(SvnUtil.appendMultiParts(repositoryRoot, path), isDirectory);
    return create(vcs, remotePath, remotePath, revision);
  }

  public static SvnRepositoryContentRevision create(@NotNull SvnVcs vcs,
                                                    @NotNull String fullPath,
                                                    @Nullable FilePath localPath,
                                                    long revision) {
    // TODO: Check if isDirectory = false always true for this method calls
    FilePath remotePath = VcsUtil.getFilePathOnNonLocal(fullPath, false);

    return create(vcs, remotePath, localPath == null ? remotePath : localPath, revision);
  }

  public static SvnRepositoryContentRevision create(@NotNull SvnVcs vcs,
                                                    @NotNull FilePath remotePath,
                                                    @Nullable FilePath localPath,
                                                    long revision) {
    return remotePath.getFileType().isBinary()
           ? new SvnRepositoryBinaryContentRevision(vcs, remotePath, localPath, revision)
           : new SvnRepositoryContentRevision(vcs, remotePath, localPath, revision);
  }

  @Override
  public String toString() {
    return myFilePath.getIOFile() + "#" + myRevision; 
  }

  private class ContentLoader implements Runnable {
    private final String myPath;
    private final long myRevision;
    private final OutputStream myDst;
    private Exception myException;

    public ContentLoader(String path, OutputStream dst, long revision) {
      myPath = path;
      myDst = dst;
      myRevision = revision;
    }

    public Exception getException() {
      return myException;
    }

    public void run() {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.loading.contents", myPath));
        progress.setText2(SvnBundle.message("progress.text2.revision.information", myRevision));
      }

      try {
        // TODO: Local path could also be used here
        SVNRevision revision = SVNRevision.create(myRevision);
        byte[] contents = SvnUtil.getFileContents(myVcs, SvnTarget.fromURL(SvnUtil.parseUrl(getFullPath())), revision, revision);
        myDst.write(contents);
      }
      catch (VcsException e) {
        myException = e;
      }
      catch (IOException e) {
        myException = e;
      }
    }
  }

  @NotNull
  public String getFullPath() {
    return myPath;
  }

  public String getRelativePath(@NotNull String repositoryUrl) {
    return SvnUtil.getRelativePath(repositoryUrl, myPath);
  }

  @Override
  public VcsKey getVcsKey() {
    return SvnVcs.getKey();
  }

  @NotNull
  public SvnTarget toTarget() throws SvnBindException {
    return SvnTarget.fromURL(SvnUtil.createUrl(getFullPath()), getRevisionNumber().getRevision());
  }
}
