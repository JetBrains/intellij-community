// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.info;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.lock.Lock;

import java.io.File;

import static com.intellij.util.ObjectUtils.notNull;

public class Info extends BaseNodeDescription {

  public static final String SCHEDULE_ADD = "add";

  private final File myFile;
  private final Url myURL;
  @NotNull private final Revision myRevision;
  private final Url myRepositoryRootURL;
  private final String myRepositoryUUID;
  @NotNull private final CommitInfo myCommitInfo;
  @Nullable private final Lock myLock;
  private final String mySchedule;
  private final Url myCopyFromURL;
  private final Revision myCopyFromRevision;
  @Nullable private final File myConflictOldFile;
  @Nullable private final File myConflictNewFile;
  @Nullable private final File myConflictWrkFile;
  private final Depth myDepth;
  @Nullable private final TreeConflictDescription myTreeConflict;

  public Info(File file,
              Url url,
              Url rootURL,
              long revision,
              @NotNull NodeKind kind,
              String uuid,
              @Nullable CommitInfo commitInfo,
              String schedule,
              Url copyFromURL,
              long copyFromRevision,
              @Nullable String conflictOldFileName,
              @Nullable String conflictNewFileName,
              @Nullable String conflictWorkingFileName,
              @Nullable Lock lock,
              Depth depth,
              @Nullable TreeConflictDescription treeConflict) {
    super(kind);
    myFile = file;
    myURL = url;
    myRevision = Revision.of(revision);
    myRepositoryUUID = uuid;
    myRepositoryRootURL = rootURL;

    myCommitInfo = notNull(commitInfo, CommitInfo.EMPTY);
    mySchedule = schedule;

    myCopyFromURL = copyFromURL;
    myCopyFromRevision = Revision.of(copyFromRevision);

    myLock = lock;
    myTreeConflict = treeConflict;

    myConflictOldFile = resolveConflictFile(file, conflictOldFileName);
    myConflictNewFile = resolveConflictFile(file, conflictNewFileName);
    myConflictWrkFile = resolveConflictFile(file, conflictWorkingFileName);

    myDepth = depth;
  }

  public Info(Url url,
              @NotNull Revision revision,
              @NotNull NodeKind kind,
              String uuid,
              Url reposRootURL,
              @Nullable CommitInfo commitInfo,
              @Nullable Lock lock,
              Depth depth) {
    super(kind);
    myURL = url;
    myRevision = revision;
    myRepositoryRootURL = reposRootURL;
    myRepositoryUUID = uuid;

    myCommitInfo = notNull(commitInfo, CommitInfo.EMPTY);
    myLock = lock;
    myDepth = depth;

    myFile = null;
    mySchedule = null;
    myCopyFromURL = null;
    myCopyFromRevision = null;
    myConflictOldFile = null;
    myConflictNewFile = null;
    myConflictWrkFile = null;
    myTreeConflict = null;
  }

  @NotNull
  public CommitInfo getCommitInfo() {
    return myCommitInfo;
  }

  @Nullable
  public File getConflictNewFile() {
    return myConflictNewFile;
  }

  @Nullable
  public File getConflictOldFile() {
    return myConflictOldFile;
  }

  @Nullable
  public File getConflictWrkFile() {
    return myConflictWrkFile;
  }

  @Nullable
  public TreeConflictDescription getTreeConflict() {
    return myTreeConflict;
  }

  public Revision getCopyFromRevision() {
    return myCopyFromRevision;
  }

  public Url getCopyFromURL() {
    return myCopyFromURL;
  }

  public File getFile() {
    return myFile;
  }

  @NotNull
  public NodeKind getKind() {
    return myKind;
  }

  @Nullable
  public Lock getLock() {
    return myLock;
  }

  public Url getRepositoryRootURL() {
    return myRepositoryRootURL;
  }

  public String getRepositoryUUID() {
    return myRepositoryUUID;
  }

  @NotNull
  public Revision getRevision() {
    return myRevision;
  }

  public String getSchedule() {
    return mySchedule;
  }

  public Url getURL() {
    return myURL;
  }

  public Depth getDepth() {
    return myDepth;
  }

  @Nullable
  private static File resolveConflictFile(@Nullable File file, @Nullable String path) {
    return file != null && path != null ? new File(file.getParentFile(), path) : null;
  }
}
