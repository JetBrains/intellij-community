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

package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;

/**
 * @author yole
*/
class SvnContentRevision implements ContentRevision {
  private final SvnVcs myVcs;
  protected final FilePath myFile;
  private SoftReference<String> myContent;
  private final SVNRevision myRevision;
  private final boolean myUseBaseRevision;

  protected SvnContentRevision(SvnVcs vcs, @NotNull final FilePath file, final SVNRevision revision, final boolean useBaseRevision) {
    myVcs = vcs;
    myRevision = revision;
    myUseBaseRevision = useBaseRevision;
    myFile = file;
  }

  public static SvnContentRevision create(@NotNull SvnVcs vcs, @NotNull final FilePath file, final SVNRevision revision) {
    if (file.getFileType().isBinary()) {
      return new SvnBinaryContentRevision(vcs, file, revision, true);
    }
    return new SvnContentRevision(vcs, file, revision, true);
  }

  public static SvnContentRevision createRemote(@NotNull SvnVcs vcs, @NotNull final FilePath file, final SVNRevision revision) {
    if (file.getFileType().isBinary()) {
      return new SvnBinaryContentRevision(vcs, file, revision, false);
    }
    return new SvnContentRevision(vcs, file, revision, false);
  }

  @Nullable
  public String getContent() throws VcsException {
    SoftReference<String> ref = myContent;
    String content = ref == null ? null : ref.get();
    if (content == null) {
      try {
        final byte[] byteContent = getUpToDateBinaryContent();
        if (byteContent != null) {
          content = new String(byteContent, myFile.getCharset().name());
          myContent = new SoftReference<String>(content);
        }
      }
      catch(Exception ex) {
        throw new VcsException(ex);
      }
    }
    return content;
  }

  @Nullable
  protected byte[] getUpToDateBinaryContent() throws SVNException, IOException {
    File file = myFile.getIOFile();
    File lock = new File(file.getParentFile(), SvnUtil.PATH_TO_LOCK_FILE);
    if (lock.exists()) {
      return null;
    }
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    SVNWCClient wcClient = myVcs.createWCClient();
    wcClient.doGetFileContents(file, SVNRevision.UNDEFINED, myUseBaseRevision ? SVNRevision.BASE : myRevision, true, buffer);
    buffer.close();
    return buffer.toByteArray();
  }

  @NotNull
  public FilePath getFile() {
    return myFile;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return new SvnRevisionNumber(myRevision);
  }

  @NonNls
  public String toString() {
    return myFile.getPath();
  }
}
