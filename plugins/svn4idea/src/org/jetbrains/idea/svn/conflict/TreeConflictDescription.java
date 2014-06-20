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
package org.jetbrains.idea.svn.conflict;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNOperation;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class TreeConflictDescription {

  private final File myPath;
  private final SVNNodeKind myNodeKind;
  private final SVNConflictAction myConflictAction;
  private final SVNConflictReason myConflictReason;

  private final SVNOperation myOperation;
  private final SVNConflictVersion mySourceLeftVersion;
  private final SVNConflictVersion mySourceRightVersion;

  @NotNull
  public static TreeConflictDescription create(@NotNull SVNTreeConflictDescription conflict) {
    return new TreeConflictDescription(conflict.getPath(), conflict.getNodeKind(), conflict.getConflictAction(),
                                       conflict.getConflictReason(), conflict.getOperation(), conflict.getSourceLeftVersion(),
                                       conflict.getSourceRightVersion());
  }

  public TreeConflictDescription(File path, SVNNodeKind nodeKind, SVNConflictAction conflictAction, SVNConflictReason conflictReason,
                                 SVNOperation operation, SVNConflictVersion sourceLeftVersion, SVNConflictVersion sourceRightVersion) {
    myPath = path;
    myNodeKind = nodeKind;
    myConflictAction = conflictAction;
    myConflictReason = conflictReason;

    myOperation = operation;
    mySourceLeftVersion = sourceLeftVersion;
    mySourceRightVersion = sourceRightVersion;
  }

  // TODO: is*Conflict() methods are not really necessary in any logic - remove them
  public boolean isTextConflict() {
    return false;
  }

  public boolean isPropertyConflict() {
    return false;
  }

  public boolean isTreeConflict() {
    return true;
  }

  public File getPath() {
    return myPath;
  }

  public SVNConflictAction getConflictAction() {
    return myConflictAction;
  }

  public SVNConflictReason getConflictReason() {
    return myConflictReason;
  }

  public SVNNodeKind getNodeKind() {
    return myNodeKind;
  }

  public SVNOperation getOperation() {
    return myOperation;
  }

  public SVNConflictVersion getSourceLeftVersion() {
    return mySourceLeftVersion;
  }

  public SVNConflictVersion getSourceRightVersion() {
    return mySourceRightVersion;
  }

  @NotNull
  public String toPresentableString() {
    // TODO: In SVNTreeConflictUtil.getHumanReadableConflictDescription "ed" ending was also removed from action and reason.
    return "local " + getConflictReason() + ", incoming " + getConflictAction() + " upon " + getOperation();
  }
}
