// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.info;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.conflict.ConflictAction;
import org.jetbrains.idea.svn.conflict.ConflictOperation;
import org.jetbrains.idea.svn.conflict.ConflictReason;
import org.jetbrains.idea.svn.lock.Lock;
import org.xml.sax.SAXException;

import java.io.File;
import java.util.Date;

import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class SvnInfoStructure {

  @Nullable public File myFile;
  public String relativeUrl;
  public Url myUrl;
  public Url myRootURL;
  public long myRevision;
  public NodeKind myKind;
  public String myUuid;
  public long myCommittedRevision;
  public String myCommittedDate;
  public String myAuthor;
  public String mySchedule;
  public Url myCopyFromURL;
  public long myCopyFromRevision;
  public String myTextTime;
  public String myPropTime;
  public String myChecksum;
  public String myConflictOld;
  public String myConflictNew;
  public String myConflictWorking;
  public String myPropRejectFile;
  public Lock.Builder myLockBuilder;
  public Depth myDepth;
  public String myChangelistName;
  public long myWcSize;
  public Date myCorrectCommittedDate;
  public Date myCorrectTextDate;

  public TreeConflictDescription myTreeConflict;

  public Info convert() throws SAXException, SvnBindException {
    return new Info(myFile, myUrl, myRootURL, myRevision, myKind, myUuid, myCommittedRevision, myCommittedDate, myAuthor, mySchedule,
                           myCopyFromURL, myCopyFromRevision, myConflictOld, myConflictNew, myConflictWorking,
                           myPropRejectFile, getLock(), myDepth, createTreeConflict());
  }

  @Nullable
  private Lock getLock() {
    return myLockBuilder != null ? myLockBuilder.build() : null;
  }

  private org.jetbrains.idea.svn.conflict.TreeConflictDescription createTreeConflict() throws SAXException, SvnBindException {
    if (myTreeConflict == null) {
      return null;
    }
    else {
      assert myFile != null;

      return new org.jetbrains.idea.svn.conflict.TreeConflictDescription(myFile, myKind, ConflictAction.from(myTreeConflict.myAction),
                                                                         ConflictReason.from(myTreeConflict.myReason),
                                                                         ConflictOperation.from(myTreeConflict.myOperation),
                                                                         createVersion(myTreeConflict.mySourceLeft),
                                                                         createVersion(myTreeConflict.mySourceRight));
    }
  }

  private org.jetbrains.idea.svn.conflict.ConflictVersion createVersion(final ConflictVersion version)
    throws SvnBindException, SAXException {
    return version == null
           ? null
           : new org.jetbrains.idea.svn.conflict.ConflictVersion(createUrl(version.myRepoUrl), version.myPathInRepo,
                                                                 parseRevision(version.myRevision), NodeKind.from(version.myKind));
  }
  
  private long parseRevision(final String revision) throws SAXException {
    try {
      return Long.parseLong(revision);
    } catch (NumberFormatException e) {
      throw new SAXException(e);
    }
  }

  public static class TreeConflictDescription {
    public String myOperation;
    public String myKind;
    public String myReason;
    public String myVictim;
    public String myAction;

    public ConflictVersion mySourceLeft;
    public ConflictVersion mySourceRight;
  }
  
  public static class ConflictVersion {
    public String myKind;
    public String myPathInRepo;
    public String myRepoUrl;
    public String myRevision;
  }
}
