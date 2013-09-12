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
package org.jetbrains.idea.svn.commandLine;

import org.apache.subversion.javahl.ConflictDescriptor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.portable.ConflictActionConvertor;
import org.jetbrains.idea.svn.portable.IdeaSVNInfo;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.wc.*;
import org.xml.sax.SAXException;

import java.io.File;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/27/12
 * Time: 2:11 PM
 */
public class SvnInfoStructure {
  @Nullable public File myFile;
  public String relativeUrl;
  public SVNURL myUrl;
  public SVNURL myRootURL;
  public long myRevision;
  public SVNNodeKind myKind;
  public String myUuid;
  public long myCommittedRevision;
  public Date myCommittedDate;
  public String myAuthor;
  public String mySchedule;
  public SVNURL myCopyFromURL;
  public long myCopyFromRevision;
  public Date myTextTime;
  public String myPropTime;
  public String myChecksum;
  public String myConflictOld;
  public String myConflictNew;
  public String myConflictWorking;
  public String myPropRejectFile;
  public SVNLockWrapper myLockWrapper;
  public SVNDepth myDepth;
  public String myChangelistName;
  public long myWcSize;
  public Date myCorrectCommittedDate;
  public Date myCorrectTextDate;

  public TreeConflictDescription myTreeConflict;

  public SVNInfo convert() throws SAXException, SVNException {
    return new IdeaSVNInfo(myFile, myUrl, myRootURL, myRevision, myKind, myUuid, myCommittedRevision, myCommittedDate, myAuthor, mySchedule,
                           myCopyFromURL, myCopyFromRevision, myTextTime, myPropTime, myChecksum, myConflictOld, myConflictNew, myConflictWorking,
                           myPropRejectFile, getLock(), myDepth, myChangelistName, myWcSize, createTreeConflict());
  }

  private SVNLock getLock() {
    SVNLock lock = null;

    if (myLockWrapper != null) {
      myLockWrapper.setPath(relativeUrl);
      lock = myLockWrapper.create();
    }

    return lock;
  }

  private SVNTreeConflictDescription createTreeConflict() throws SAXException, SVNException {
    if (myTreeConflict == null) {
      return null;
    }
    else {
      assert myFile != null;

      final SVNConflictAction action = ConflictActionConvertor.create(ConflictDescriptor.Action.valueOf(myTreeConflict.myAction));
      final SVNConflictReason reason = parseConflictReason(myTreeConflict.myReason);
      //final SVNConflictReason reason = ConflictReasonConvertor.convert(ConflictDescriptor.Reason.valueOf(myTreeConflict.myReason));
      SVNOperation operation = SVNOperation.fromString(myTreeConflict.myOperation);
      operation = operation == null ? SVNOperation.NONE : operation;
      return new SVNTreeConflictDescription(myFile, myKind, action, reason, operation,
                                            createVersion(myTreeConflict.mySourceLeft),
                                            createVersion(myTreeConflict.mySourceRight));
    }
  }

  private SVNConflictReason parseConflictReason(String reason) throws SAXException {
    if (ConflictDescriptor.Reason.edited.name().equals(reason)) {
      return SVNConflictReason.EDITED;
    } else if (ConflictDescriptor.Reason.obstructed.name().equals(reason)) {
      return SVNConflictReason.OBSTRUCTED;
    } else if (ConflictDescriptor.Reason.deleted.name().equals(reason)) {
      return SVNConflictReason.DELETED;
    } else if (ConflictDescriptor.Reason.missing.name().equals(reason)) {
      return SVNConflictReason.MISSING;
    } else if (ConflictDescriptor.Reason.unversioned.name().equals(reason)) {
      return SVNConflictReason.UNVERSIONED;
    } else if (ConflictDescriptor.Reason.added.name().equals(reason)) {
      return SVNConflictReason.ADDED;
    } else if (ConflictDescriptor.Reason.replaced.name().equals(reason)) {
      return SVNConflictReason.REPLACED;
    }
    if ("edit".equals(reason)) {
      return SVNConflictReason.EDITED;
    } else if (reason.contains("obstruct")) {
      return SVNConflictReason.OBSTRUCTED;
    } else if ("delete".equals(reason)) {
      return SVNConflictReason.DELETED;
    } else if (reason.contains("miss")) {
      return SVNConflictReason.MISSING;
    } else if (reason.contains("unversion")) {
      return SVNConflictReason.UNVERSIONED;
    } else if (reason.contains("add")) {
      return SVNConflictReason.ADDED;
    } else if (reason.contains("replace")) {
      return SVNConflictReason.REPLACED;
    }
    throw new SAXException("Can not parse conflict reason: " + reason);
  }

  private SVNConflictVersion createVersion(final ConflictVersion version) throws SVNException, SAXException {
    return version == null ? null : new SVNConflictVersion(SVNURL.parseURIEncoded(version.myRepoUrl), version.myPathInRepo,
                                                           parseRevision(version.myRevision), SVNNodeKind.parseKind(version.myKind));
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
