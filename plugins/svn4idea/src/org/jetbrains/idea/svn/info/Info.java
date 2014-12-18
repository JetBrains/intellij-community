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
import org.jetbrains.idea.svn.api.BaseNodeDescription;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.lock.Lock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.Date;

/**
 * @author Konstantin Kolosovsky.
 */
public class Info extends BaseNodeDescription {

  public static final String SCHEDULE_ADD = "add";

  private final File myFile;
  private final String myPath;
  private final SVNURL myURL;
  @NotNull private final SVNRevision myRevision;
  private final SVNURL myRepositoryRootURL;
  private final String myRepositoryUUID;
  private final SVNRevision myCommittedRevision;
  private final Date myCommittedDate;
  private final String myAuthor;
  @Nullable private final Lock myLock;
  private final boolean myIsRemote;
  private final String mySchedule;
  private final SVNURL myCopyFromURL;
  private final SVNRevision myCopyFromRevision;
  @Nullable private final File myConflictOldFile;
  @Nullable private final File myConflictNewFile;
  @Nullable private final File myConflictWrkFile;
  @Nullable private final File myPropConflictFile;
  private final Depth myDepth;
  @Nullable private final TreeConflictDescription myTreeConflict;

  @NotNull
  public static Info create(@NotNull SVNInfo info) {
    Info result;

    if (info.isRemote()) {
      result = new Info(info.getPath(), info.getURL(), info.getRevision(), NodeKind.from(info.getKind()), info.getRepositoryUUID(),
                        info.getRepositoryRootURL(), info.getCommittedRevision().getNumber(), info.getCommittedDate(), info.getAuthor(),
                        Lock.create(info.getLock()), Depth.from(info.getDepth()));
    }
    else {
      result =
        new Info(info.getFile(), info.getURL(), info.getRepositoryRootURL(), info.getRevision().getNumber(), NodeKind.from(info.getKind()),
                 info.getRepositoryUUID(), info.getCommittedRevision().getNumber(), toString(info.getCommittedDate()), info.getAuthor(),
                 info.getSchedule(), info.getCopyFromURL(), info.getCopyFromRevision().getNumber(), getName(info.getConflictOldFile()),
                 getName(info.getConflictNewFile()), getName(info.getConflictWrkFile()), getName(info.getPropConflictFile()),
                 Lock.create(info.getLock()), Depth.from(info.getDepth()), TreeConflictDescription.create(info.getTreeConflict()));
    }

    return result;
  }

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
    myRevision = SVNRevision.create(revision);
    myRepositoryUUID = uuid;
    myRepositoryRootURL = rootURL;

    myCommittedRevision = SVNRevision.create(committedRevision);
    myCommittedDate = committedDate != null ? SVNDate.parseDate(committedDate) : null;
    myAuthor = author;

    mySchedule = schedule;

    myCopyFromURL = copyFromURL;
    myCopyFromRevision = SVNRevision.create(copyFromRevision);

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
              @NotNull SVNRevision revision,
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
    myCommittedRevision = SVNRevision.create(committedRevision);
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

  public SVNRevision getCommittedRevision() {
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

  public SVNRevision getCopyFromRevision() {
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
  public SVNRevision getRevision() {
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

  @Nullable
  private static String getName(@Nullable File file) {
    return file != null ? file.getName() : null;
  }

  @Nullable
  private static String toString(@Nullable Date date) {
    return date != null ? date.toString() : null;
  }
}
