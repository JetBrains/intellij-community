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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseNodeDescription;
import org.jetbrains.idea.svn.api.NodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;

/**
 * @author Konstantin Kolosovsky.
 */
public class ConflictVersion extends BaseNodeDescription {

  private final SVNURL myRepositoryRoot;
  private final String myPath;
  private final long myPegRevision;

  @Nullable
  public static ConflictVersion create(@Nullable SVNConflictVersion conflictVersion) {
    ConflictVersion result = null;

    if (conflictVersion != null) {
      result = new ConflictVersion(conflictVersion.getRepositoryRoot(), conflictVersion.getPath(), conflictVersion.getPegRevision(),
                                   NodeKind.from(conflictVersion.getKind()));
    }

    return result;
  }

  public ConflictVersion(SVNURL repositoryRoot, String path, long pegRevision, @NotNull NodeKind kind) {
    super(kind);
    myRepositoryRoot = repositoryRoot;
    myPath = path;
    myPegRevision = pegRevision;
  }

  public SVNURL getRepositoryRoot() {
    return myRepositoryRoot;
  }

  public String getPath() {
    return myPath;
  }

  public long getPegRevision() {
    return myPegRevision;
  }

  @NotNull
  public NodeKind getKind() {
    return myKind;
  }

  @NotNull
  public String toPresentableString() {
    StringBuilder urlBuilder = new StringBuilder();

    urlBuilder.append(myRepositoryRoot != null ? myRepositoryRoot : "");
    urlBuilder.append("/");
    urlBuilder.append(myPath != null ? myPath : "...");

    return "(" + getKind() + ") " + urlBuilder + "@" + getPegRevision();
  }

  @NotNull
  public static String toPresentableString(@Nullable ConflictVersion version) {
    return version == null ? "" : version.toPresentableString();
  }
}
