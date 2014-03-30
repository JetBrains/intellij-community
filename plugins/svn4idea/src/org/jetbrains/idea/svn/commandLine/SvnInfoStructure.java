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

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.portable.IdeaSVNInfo;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.wc.*;
import org.xml.sax.SAXException;

import java.io.File;
import java.util.Date;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/27/12
 * Time: 2:11 PM
 */
public class SvnInfoStructure {

  private static final Map<String, SVNConflictAction> ourConflictActions = ContainerUtil.newHashMap();
  private static final Map<String, SVNConflictReason> ourConflictReasons = ContainerUtil.newHashMap();

  static {
    ourConflictActions.put("add", SVNConflictAction.ADD);
    ourConflictActions.put("edit", SVNConflictAction.EDIT);
    ourConflictActions.put("delete", SVNConflictAction.DELETE);
    ourConflictActions.put("replace", SVNConflictAction.REPLACE);

    ourConflictReasons.put("edit", SVNConflictReason.EDITED);
    ourConflictReasons.put("obstruct", SVNConflictReason.OBSTRUCTED);
    ourConflictReasons.put("delete", SVNConflictReason.DELETED);
    ourConflictReasons.put("miss", SVNConflictReason.MISSING);
    ourConflictReasons.put("unversion", SVNConflictReason.UNVERSIONED);
    ourConflictReasons.put("add", SVNConflictReason.ADDED);
    ourConflictReasons.put("replace", SVNConflictReason.REPLACED);
  }

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

      final SVNConflictAction action = parseConflictAction(myTreeConflict.myAction);
      final SVNConflictReason reason = parseConflictReason(myTreeConflict.myReason);
      SVNOperation operation = SVNOperation.fromString(myTreeConflict.myOperation);
      operation = operation == null ? SVNOperation.NONE : operation;
      return new SVNTreeConflictDescription(myFile, myKind, action, reason, operation,
                                            createVersion(myTreeConflict.mySourceLeft),
                                            createVersion(myTreeConflict.mySourceRight));
    }
  }

  private SVNConflictAction parseConflictAction(@NotNull String actionName) {
    SVNConflictAction action = SVNConflictAction.fromString(actionName);
    action = action != null ? action : ourConflictActions.get(actionName);

    if (action == null) {
      throw new IllegalArgumentException("Unknown conflict action " + actionName);
    }

    return action;
  }

  private SVNConflictReason parseConflictReason(@NotNull String reasonName) throws SAXException {
    SVNConflictReason reason = SVNConflictReason.fromString(reasonName);
    reason = reason != null ? reason : ourConflictReasons.get(reasonName);

    if (reason == null) {
      throw new SAXException("Can not parse conflict reason: " + reasonName);
    }

    return reason;
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
