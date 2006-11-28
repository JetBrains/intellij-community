/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.OutputStream;
import java.io.File;
import java.io.ByteArrayOutputStream;

public class SvnContentRevision implements ContentRevision {
  private SVNRepository myRepository;
  private SVNLogEntryPath myLogEntryPath;
  private long myRevision;
  private String myContent;

  public SvnContentRevision(final SVNRepository repository, final SVNLogEntryPath logEntryPath, final long revision) {
    myLogEntryPath = logEntryPath;
    myRepository = repository;
    myRevision = revision;
  }

  @Nullable
  public String getContent() {
    if (myContent == null) {
      final String path = myLogEntryPath.getPath();
      final OutputStream buffer = new ByteArrayOutputStream();
      ContentLoader loader = new ContentLoader(path, buffer, myRevision);
      if (ApplicationManager.getApplication().isDispatchThread()) {
        ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(loader, SvnBundle.message("progress.title.loading.file.content"), false, null);
      }
      else {
        loader.run();
      }
      if (loader.getException() == null) {
        myContent = buffer.toString();
      }
    }
    return myContent;
  }

  @NotNull
  public FilePath getFile() {
    return PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(new File(myLogEntryPath.getPath()));
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return new SvnRevisionNumber(SVNRevision.create(myRevision));
  }

  private class ContentLoader implements Runnable {
    private String myPath;
    private long myRevision;
    private OutputStream myDst;
    private SVNException myException;

    public ContentLoader(String path, OutputStream dst, long revision) {
      myPath = path;
      myDst = dst;
      myRevision = revision;
    }

    public SVNException getException() {
      return myException;
    }

    public void run() {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.loading.contents", myPath));
        progress.setText2(SvnBundle.message("progress.text2.revision.information", myRevision));
      }
      try {
        myRepository.getFile(myPath, myRevision, null, myDst);
      }
      catch (SVNException e) {
        myException = e;
      }
    }
  }
}