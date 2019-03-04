// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vcs.impl.CurrentRevisionProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.status.Status;

import java.io.File;
import java.io.IOException;

public class SvnContentRevision extends SvnBaseContentRevision implements ByteBackedContentRevision {

  @NotNull private final Revision myRevision;
  /**
   * this flag is necessary since SVN would not do remote request only if constant Revision.BASE
   * -> usual current revision content class can't be used
   */
  private final boolean myUseBaseRevision;

  protected SvnContentRevision(@NotNull SvnVcs vcs, @NotNull FilePath file, @NotNull Revision revision, boolean useBaseRevision) {
    super(vcs, file);
    myRevision = revision;
    myUseBaseRevision = useBaseRevision;
  }

  @NotNull
  public static SvnContentRevision createBaseRevision(@NotNull SvnVcs vcs, @NotNull FilePath file, @NotNull Status status) {
    Revision revision = status.getRevision().isValid() ? status.getRevision() : status.getCommitInfo().getRevision();
    return createBaseRevision(vcs, file, revision);
  }

  @NotNull
  public static SvnContentRevision createBaseRevision(@NotNull SvnVcs vcs, @NotNull FilePath file, @NotNull Revision revision) {
    if (file.getFileType().isBinary()) {
      return new SvnBinaryContentRevision(vcs, file, revision, true);
    }
    return new SvnContentRevision(vcs, file, revision, true);
  }

  @NotNull
  public static SvnContentRevision createRemote(@NotNull SvnVcs vcs, @NotNull FilePath file, @NotNull Revision revision) {
    if (file.getFileType().isBinary()) {
      return new SvnBinaryContentRevision(vcs, file, revision, false);
    }
    return new SvnContentRevision(vcs, file, revision, false);
  }

  @Override
  @Nullable
  public String getContent() throws VcsException {
    return ContentRevisionCache.getAsString(getContentAsBytes(), myFile, null);
  }

  @Nullable
  @Override
  public byte[] getContentAsBytes() throws VcsException {
    try {
      if (myUseBaseRevision) {
        return ContentRevisionCache.getOrLoadCurrentAsBytes(myVcs.getProject(), myFile, myVcs.getKeyInstanceMethod(),
                                                            new CurrentRevisionProvider() {
                                                              @Override
                                                              public VcsRevisionNumber getCurrentRevision() {
                                                                return getRevisionNumber();
                                                              }

                                                              @Override
                                                              public Pair<VcsRevisionNumber, byte[]> get()
                                                                throws VcsException {
                                                                return Pair.create(getRevisionNumber(), getUpToDateBinaryContent());
                                                              }
                                                            }).getSecond();
      } else {
        return ContentRevisionCache.getOrLoadAsBytes(myVcs.getProject(), myFile, getRevisionNumber(), myVcs.getKeyInstanceMethod(),
                                                     ContentRevisionCache.UniqueType.REPOSITORY_CONTENT, () -> getUpToDateBinaryContent());
      }
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  private byte[] getUpToDateBinaryContent() throws VcsException {
    File file = myFile.getIOFile();
    File lock = new File(file.getParentFile(), SvnUtil.PATH_TO_LOCK_FILE);
    if (lock.exists()) {
      throw new VcsException("Can not access file base revision contents: administrative area is locked");
    }
    return SvnUtil.getFileContents(myVcs, Target.on(file), myUseBaseRevision ? Revision.BASE : myRevision,
                                   Revision.UNDEFINED);
  }

  @Override
  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return new SvnRevisionNumber(myRevision);
  }

  @NonNls
  public String toString() {
    return myFile.getPath();
  }
}
