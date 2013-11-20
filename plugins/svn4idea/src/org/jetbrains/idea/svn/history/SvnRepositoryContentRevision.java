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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.MarkerVcsContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class SvnRepositoryContentRevision implements ContentRevision, MarkerVcsContentRevision {
  private final String myRepositoryRoot;
  private final SvnVcs myVcs;
  private final String myPath;
  @NotNull private final FilePath myFilePath;
  private final long myRevision;

  SvnRepositoryContentRevision(final SvnVcs vcs, final String repositoryRoot, final String path, @Nullable final FilePath localPath,
                                      final long revision) {
    myVcs = vcs;
    myPath = path;
    myRepositoryRoot = repositoryRoot;
    if (localPath != null) {
      myFilePath = localPath;
    }
    else {
      FilePath local;
      try {
        final String fullPath = SvnUtil.appendMultiParts(repositoryRoot, myPath);
        local = VcsContextFactory.SERVICE.getInstance().createFilePathOnNonLocal(fullPath, false);
      }
      catch (SVNException e) {
        // todo what to do safely?
        local = VcsContextFactory.SERVICE.getInstance().createFilePathOnNonLocal(repositoryRoot, false);
      }
      myFilePath = local;
    }
    myRevision = revision;
  }

  @Nullable
  public String getContent() throws VcsException {
    try {
      myFilePath.hardRefresh();
      return ContentRevisionCache.getOrLoadAsString(myVcs.getProject(), myFilePath, getRevisionNumber(), myVcs.getKeyInstanceMethod(),
                                             ContentRevisionCache.UniqueType.REPOSITORY_CONTENT, new Throwable2Computable<byte[], VcsException, IOException>() {
        @Override
        public byte[] compute() throws VcsException, IOException {
          final ByteArrayOutputStream buffer = loadContent();
          return buffer.toByteArray();
        }
      });
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

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
  public VcsRevisionNumber getRevisionNumber() {
    return new SvnRevisionNumber(SVNRevision.create(myRevision));
  }

  public static SvnRepositoryContentRevision create(final SvnVcs vcs, final String repositoryRoot, final String path,
                                                    @Nullable final FilePath localPath, final long revision) {
    int fileNamePos = path.lastIndexOf('/');
    if (fileNamePos >= 0) {
      String fileName = path.substring(fileNamePos);
      final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
      if (fileType.isBinary()) {
        return new SvnRepositoryBinaryContentRevision(vcs, repositoryRoot, path, localPath, revision);
      }
    }
    return new SvnRepositoryContentRevision(vcs, repositoryRoot, path, localPath, revision);
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

  public String getFullPath() {
    String fullPath = myRepositoryRoot;
    if (!fullPath.endsWith("/") && !myPath.startsWith("/")) {
      fullPath += "/";
    }
    fullPath += myPath;
    return fullPath;
  }

  public String getPath() {
    return myPath;
  }

  @Override
  public VcsKey getVcsKey() {
    return SvnVcs.getKey();
  }
}
