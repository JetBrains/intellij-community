/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.info;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.BaseNodeDescription;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.lock.Lock;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.time.Instant;
import java.util.Date;

import static com.intellij.util.ObjectUtils.notNull;

public class Info extends BaseNodeDescription {

  public static final String SCHEDULE_ADD = "add";

  private static final Date DEFAULT_COMMITTED_DATE = Date.from(Instant.EPOCH);

  private final File myFile;
  private final String myPath;
  private final SVNURL myURL;
  @NotNull private final Revision myRevision;
  private final SVNURL myRepositoryRootURL;
  private final String myRepositoryUUID;
  private final Revision myCommittedRevision;
  private final Date myCommittedDate;
  private final String myAuthor;
  @Nullable private final Lock myLock;
  private final boolean myIsRemote;
  private final String mySchedule;
  private final SVNURL myCopyFromURL;
  private final Revision myCopyFromRevision;
  @Nullable private final File myConflictOldFile;
  @Nullable private final File myConflictNewFile;
  @Nullable private final File myConflictWrkFile;
  @Nullable private final File myPropConflictFile;
  private final Depth myDepth;
  @Nullable private final TreeConflictDescription myTreeConflict;

  public Info(File file,
              SVNURL url,
              SVNURL rootURL,
              long revision,
              @NotNull NodeKind kind,
              String uuid,
              long committedRevision,
              String committedDate,
              String author,
              String schedule,
              SVNURL copyFromURL,
              long copyFromRevision,
              @Nullable String conflictOldFileName,
              @Nullable String conflictNewFileName,
              @Nullable String conflictWorkingFileName,
              @Nullable String propRejectFileName,
              @Nullable Lock lock,
              Depth depth,
              @Nullable TreeConflictDescription treeConflict) {
    super(kind);
    myFile = file;
    myURL = url;
    myRevision = Revision.of(revision);
    myRepositoryUUID = uuid;
    myRepositoryRootURL = rootURL;

    myCommittedRevision = Revision.of(committedRevision);
    myCommittedDate = committedDate != null ? notNull(SvnUtil.parseDate(committedDate), DEFAULT_COMMITTED_DATE) : null;
    myAuthor = author;

    mySchedule = schedule;

    myCopyFromURL = copyFromURL;
    myCopyFromRevision = Revision.of(copyFromRevision);

    myLock = lock;
    myTreeConflict = treeConflict;

    myConflictOldFile = resolveConflictFile(file, conflictOldFileName);
    myConflictNewFile = resolveConflictFile(file, conflictNewFileName);
    myConflictWrkFile = resolveConflictFile(file, conflictWorkingFileName);
    myPropConflictFile = resolveConflictFile(file, propRejectFileName);

    myIsRemote = false;
    myDepth = depth;

    myPath = null;
  }

  public Info(String path,
              SVNURL url,
              @NotNull Revision revision,
              @NotNull NodeKind kind,
              String uuid,
              SVNURL reposRootURL,
              long committedRevision,
              Date date,
              String author,
              @Nullable Lock lock,
              Depth depth) {
    super(kind);
    myIsRemote = true;
    myURL = url;
    myRevision = revision;
    myRepositoryRootURL = reposRootURL;
    myRepositoryUUID = uuid;

    myCommittedDate = date;
    myCommittedRevision = Revision.of(committedRevision);
    myAuthor = author;

    myLock = lock;
    myPath = path;
    myDepth = depth;

    myFile = null;
    mySchedule = null;
    myCopyFromURL = null;
    myCopyFromRevision = null;
    myConflictOldFile = null;
    myConflictNewFile = null;
    myConflictWrkFile = null;
    myPropConflictFile = null;
    myTreeConflict = null;
  }

  public String getAuthor() {
    return myAuthor;
  }

  public Date getCommittedDate() {
    return myCommittedDate;
  }

  public Revision getCommittedRevision() {
    return myCommittedRevision;
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

  public SVNURL getCopyFromURL() {
    return myCopyFromURL;
  }

  public File getFile() {
    return myFile;
  }

  public boolean isRemote() {
    return myIsRemote;
  }

  @NotNull
  public NodeKind getKind() {
    return myKind;
  }

  @Nullable
  public Lock getLock() {
    return myLock;
  }

  public String getPath() {
    return myPath;
  }

  @Nullable
  public File getPropConflictFile() {
    return myPropConflictFile;
  }

  public SVNURL getRepositoryRootURL() {
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

  public SVNURL getURL() {
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
