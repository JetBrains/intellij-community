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
package org.jetbrains.idea.svn.status;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.nio.charset.Charset;

public class DiffContentRevision implements ContentRevision {
  private String myPath;
  private SVNRepository myRepository;
  private String myContents;
  private FilePath myFilePath;
  private long myRevision;

  public DiffContentRevision(String path, @NotNull SVNRepository repos, long revision) {
    this(path, repos, revision, VcsUtil.getFilePath(path));
  }

  public DiffContentRevision(final String path, final SVNRepository repository, final long revision, final FilePath filePath) {
    myPath = path;
    myRepository = repository;
    myFilePath = filePath;
    myRevision = revision;
  }

  @Nullable
  public String getContent() throws VcsException {
    if (myContents == null) {
      BufferExposingByteArrayOutputStream bos = new BufferExposingByteArrayOutputStream(2048);
      try {
        myRepository.getFile(myPath, -1, null, bos);
        myRepository.closeSession();
      } catch (SVNException e) {
        throw new VcsException(e);
      }
      final byte[] bytes = bos.toByteArray();
      final Charset charset = myFilePath.getCharset();
      myContents = charset == null ? CharsetToolkit.bytesToString(bytes, EncodingRegistry.getInstance().getDefaultCharset()) : CharsetToolkit.bytesToString(bytes, charset);
    }
    return myContents;
  }

  @NotNull
  public FilePath getFile() {
    return myFilePath;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return new VcsRevisionNumber.Long(myRevision);
  }
}
