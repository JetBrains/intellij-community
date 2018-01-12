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
import org.jetbrains.idea.svn.api.BaseNodeDescription;
import org.jetbrains.idea.svn.api.NodeKind;

import java.io.File;

public class TreeConflictDescription extends BaseNodeDescription {

  private final File myPath;
  private final ConflictAction myConflictAction;
  private final ConflictReason myConflictReason;

  private final ConflictOperation myOperation;
  private final ConflictVersion mySourceLeftVersion;
  private final ConflictVersion mySourceRightVersion;

  public TreeConflictDescription(File path,
                                 @NotNull NodeKind nodeKind,
                                 ConflictAction conflictAction,
                                 ConflictReason conflictReason,
                                 ConflictOperation operation,
                                 ConflictVersion sourceLeftVersion,
                                 ConflictVersion sourceRightVersion) {
    super(nodeKind);
    myPath = path;
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

  public ConflictAction getConflictAction() {
    return myConflictAction;
  }

  public ConflictReason getConflictReason() {
    return myConflictReason;
  }

  @NotNull
  public NodeKind getNodeKind() {
    return myKind;
  }

  public ConflictOperation getOperation() {
    return myOperation;
  }

  public ConflictVersion getSourceLeftVersion() {
    return mySourceLeftVersion;
  }

  public ConflictVersion getSourceRightVersion() {
    return mySourceRightVersion;
  }

  @NotNull
  public String toPresentableString() {
    return "local " + getConflictReason() + ", incoming " + getConflictAction() + " upon " + getOperation();
  }
}
