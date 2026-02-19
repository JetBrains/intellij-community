// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnContentRevision extends SvnBaseContentRevision implements ByteBackedContentRevision {

  private final @NotNull Revision myRevision;
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

  public static @NotNull SvnContentRevision createBaseRevision(@NotNull SvnVcs vcs, @NotNull FilePath file, @NotNull Status status) {
    Revision revision = status.getRevision().isValid() ? status.getRevision() : status.getCommitInfo().getRevision();
    return createBaseRevision(vcs, file, revision);
  }

  public static @NotNull SvnContentRevision createBaseRevision(@NotNull SvnVcs vcs, @NotNull FilePath file, @NotNull Revision revision) {
    if (file.getFileType().isBinary()) {
      return new SvnBinaryContentRevision(vcs, file, revision, true);
    }
    return new SvnContentRevision(vcs, file, revision, true);
  }

  public static @NotNull SvnContentRevision createRemote(@NotNull SvnVcs vcs, @NotNull FilePath file, @NotNull Revision revision) {
    if (file.getFileType().isBinary()) {
      return new SvnBinaryContentRevision(vcs, file, revision, false);
    }
    return new SvnContentRevision(vcs, file, revision, false);
  }

  @Override
  public @Nullable String getContent() throws VcsException {
    return ContentRevisionCache.getAsString(getContentAsBytes(), myFile, null);
  }

  @Override
  public byte @Nullable [] getContentAsBytes() throws VcsException {
    try {
      if (myUseBaseRevision) {
        return ContentRevisionCache.getOrLoadCurrentAsBytes(myVcs.getProject(), myFile, myVcs.getKeyInstanceMethod(),
                                                            new CurrentRevisionProvider() {
                                                              @Override
                                                              public @NotNull VcsRevisionNumber getCurrentRevision() {
                                                                return getRevisionNumber();
                                                              }

                                                              @Override
                                                              public @NotNull Pair<VcsRevisionNumber, byte[]> get()
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
      throw new VcsException(message("error.can.not.access.file.base.revision.contents.administrative.area.is.locked"));
    }
    return SvnUtil.getFileContents(myVcs, Target.on(file), myUseBaseRevision ? Revision.BASE : myRevision,
                                   Revision.UNDEFINED);
  }

  @Override
  public @NotNull VcsRevisionNumber getRevisionNumber() {
    return new SvnRevisionNumber(myRevision);
  }

  @Override
  public @NonNls String toString() {
    return myFile.getPath();
  }
}
