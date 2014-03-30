/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.portable;

import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;

import java.io.File;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/23/12
 * Time: 1:02 PM
 */
public class IdeaSVNInfo extends SVNInfo {
  private final Date myCorrectCommittedDate;
  private final Date myCorrectTextDate;

  public IdeaSVNInfo(@Nullable File file,
                     SVNURL url,
                     SVNURL rootURL,
                     long revision,
                     SVNNodeKind kind,
                     String uuid,
                     long committedRevision,
                     Date committedDate,
                     String author,
                     String schedule,
                     SVNURL copyFromURL,
                     long copyFromRevision,
                     Date textTime,
                     String propTime,
                     String checksum,
                     String conflictOld,
                     String conflictNew,
                     String conflictWorking,
                     String propRejectFile,
                     SVNLock lock,
                     SVNDepth depth,
                     String changelistName,
                     long wcSize,
                     SVNTreeConflictDescription treeConflict) {
    super(file, url, rootURL, revision, kind, uuid, committedRevision, null, author, schedule, copyFromURL, copyFromRevision,
          null, propTime, checksum, conflictOld, conflictNew, conflictWorking, propRejectFile, lock, depth, changelistName, wcSize,
          treeConflict);
    myCorrectCommittedDate = committedDate;
    myCorrectTextDate = textTime;
  }

  /**
   * Gets the item's last commit date. This is the value of the item's
   * {@link org.tmatesoft.svn.core.SVNProperty#COMMITTED_DATE}
   * property.
   *
   * @return the item's last commit date
   */
  @Override
  public Date getCommittedDate() {
    return myCorrectCommittedDate;
  }

  /**
   * Gets the value of the item's {@link org.tmatesoft.svn.core.SVNProperty#TEXT_TIME}
   * property. It corresponds to the last commit time.
   *
   * @return the value of the item's text-time property
   */
  @Override
  public Date getTextTime() {
    return myCorrectTextDate;
  }

  public IdeaSVNInfo(String path,
                     SVNURL url,
                     SVNRevision revision,
                     SVNNodeKind kind,
                     String uuid,
                     SVNURL reposRootURL,
                     long comittedRevision, Date date, String author, SVNLock lock, SVNDepth depth, long size) {
    super(path, url, revision, kind, uuid, reposRootURL, comittedRevision, date, author, lock, depth, size);
    myCorrectCommittedDate = date;
    myCorrectTextDate = null;
  }
}
