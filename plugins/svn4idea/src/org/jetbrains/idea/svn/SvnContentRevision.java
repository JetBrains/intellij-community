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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vcs.impl.CurrentRevisionProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * @author yole
*/
class SvnContentRevision implements ContentRevision {
  private final SvnVcs myVcs;
  protected final FilePath myFile;
  private final SVNRevision myRevision;
  /**
   * this flag is necessary since SVN would not do remote request only if constant SVNRevision.BASE
   * -> usual current revision content class can't be used
   */
  private final boolean myUseBaseRevision;

  protected SvnContentRevision(SvnVcs vcs, @NotNull final FilePath file, final SVNRevision revision, final boolean useBaseRevision) {
    myVcs = vcs;
    myRevision = revision;
    myUseBaseRevision = useBaseRevision;
    myFile = file;
  }

  public static SvnContentRevision createBaseRevision(@NotNull SvnVcs vcs, @NotNull final FilePath file, final SVNRevision revision) {
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
    try {
      if (myUseBaseRevision) {
        return ContentRevisionCache.getOrLoadCurrentAsString(myVcs.getProject(), myFile, myVcs.getKeyInstanceMethod(),
                                                             new CurrentRevisionProvider() {
                                                               @Override
                                                               public VcsRevisionNumber getCurrentRevision() throws VcsException {
                                                                 return getRevisionNumber();
                                                               }

                                                               @Override
                                                               public Pair<VcsRevisionNumber, byte[]> get()
                                                                 throws VcsException, IOException {
                                                                 return new Pair<VcsRevisionNumber, byte[]>(getRevisionNumber(), getUpToDateBinaryContent());
                                                               }
                                                             }).getSecond();
      } else {
        return ContentRevisionCache.getOrLoadAsString(myVcs.getProject(), myFile, getRevisionNumber(), myVcs.getKeyInstanceMethod(),
                                                      ContentRevisionCache.UniqueType.REPOSITORY_CONTENT,
                                                      new Throwable2Computable<byte[], VcsException, IOException>() {
                                                        @Override
                                                        public byte[] compute() throws VcsException, IOException {
                                                          return getUpToDateBinaryContent();
                                                        }
                                                      });
      }
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  protected byte[] getUpToDateBinaryContent() throws VcsException {
    File file = myFile.getIOFile();
    File lock = new File(file.getParentFile(), SvnUtil.PATH_TO_LOCK_FILE);
    if (lock.exists()) {
      throw new VcsException("Can not access file base revision contents: administrative area is locked");
    }
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    SVNWCClient wcClient = myVcs.createWCClient();
    try {
      wcClient.doGetFileContents(file, SVNRevision.UNDEFINED, myUseBaseRevision ? SVNRevision.BASE : myRevision, true, buffer);
      buffer.close();
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
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
